package th.co.scb

import org.codehaus.groovy.runtime.NullObject

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

class PipelineContext implements Context {

    def script
    PipelineUtility util
    Map config
    Map map
    String imgName

    PipelineContext(script, Map config, PipelineUtility util) {
        this.script = script
        this.config = config
        this.util = util
        this.map = [:]
        this.imgName = ''
    }

    def assemble() {
        util.debug "Validating input ..."
        if (!config.projectName) {
            util.error "Param 'projectName' is required"
        }
        // branchToEnvironmentMapping must be given, but it is OK to be empty - e.g.
        // if the repository should not be deployed at all
        if (!config.branchToEnvironmentMapping) {
            util.error """
                ----------------------------------------------
                Param 'branchToEnvironmentMapping' is required,
                but it is OK to be empty - e.g. if the repository
                should not be deployed at all
                ----------------------------------------------
                branchToEnvironmentMapping: [
                    'master': 'prod',
                    '*': 'dev'
                ]
                ----------------------------------------------
                branchToEnvironmentMapping: [
                    "master": "prod",
                    "develop": "dev",
                    "hotfix/": "hotfix",
                    "release/": "test",
                    "*": "review"
                ]
                ----------------------------------------------
                branchToEnvironmentMapping: []
            """
        }
        // TODO: Handle no kubernetes configuration
        if (!config.kubernetes) {
            util.error """
                ----------------------------------------------
                Param 'kubernetes' is not required, but must be given
                value of 'environmentToK8sContextMapping' and
                'environmentToK8sNamespaceMapping' and must be mapped
                to environment from 'branchToEnvironmentMapping'
                ----------------------------------------------
                kubernetes: [
                  environmentToK8sContextMapping: [
                    'staging': 'k8s-stg.surepay-nonprod.com',
                    'develop': 'k8s-dev.surepay-nonprod.com',
                  ],
                  environmentToK8sNamespaceMapping: [
                    'staging': 'stg',
                    'develop': 'dev',
                  ]
                ]
                ----------------------------------------------
            """
        }
        if (!config.registry) {
            util.error """
                ----------------------------------------------
                Param 'registry' is required to create images and access
                to correct registry with correct context
                ----------------------------------------------
                Project without subgroup 
                e.g `registry.gitlab.com/company/app`
                
                registry: [
                  'url': 'registry.gitlab.com',
                  'group': 'company',
                  'project': 'app',
                  'registryCredential': '<docker id password>'
                ]
                ----------------------------------------------
                Project without subgroup 
                e.g `registry.gitlab.com/company/work/app`
                
                registry: [
                  'url': 'registry.gitlab.com',
                  'group': 'company',
                  'subgroup': 'work'
                  'project': 'app',
                  'registryCredential': '<docker id password>'
                ]
            """
        }
        if (!config.pipelineType) {
            util.error """
                ----------------------------------------------
                Param 'pipelineType' is required, must be one of
                available choices
                ----------------------------------------------
                pipelineType: 'golang'
                pipelineType: 'javascript'
            """
        }

        util.debug "Collecting environment variables ..."
        map.jobName = script.env.JOB_NAME
        map.buildNumber = script.env.BUILD_NUMBER
        map.buildUrl = script.env.BUILD_URL
        map.buildTime = new Date()

        util.debug "Validating environment variables ..."
        if (!map.jobName) {
            util.error 'JOB_NAME is required, but not set (usually provided by Jenkins)'
        }
        if (!map.buildNumber) {
            util.error 'BUILD_NUMBER is required, but not set (usually provided by Jenkins)'
        }
        if (!map.buildUrl) {
            util.info """
                ----------------------------------------------
                BUILD_URL is required to set a proper build status in
                gitUrl, but it is not present. Normally, it is provided
                by Jenkins - please check your JenkinsUrl configuration.
            """
        }

        util.debug "Retrieving Git information ..."
        map.gitBranch = script.env.BRANCH_NAME
        map.gitBranchName = extractBranchCode(map.gitBranch)
        map.gitCommit = retrieveGitCommit()
        map.gitCommitAuthor = retrieveGitCommitAuthor()
        map.gitCommitMessage = retrieveGitCommitMessage()
        map.gitCommitTime = retrieveGitCommitTime()
        map.gitUrl = retrieveGitUrl()
        map.gitCiSkip = retrieveGitCiSkip()
        map.tagVersion = determineTagVersion()
        if ( config.s3Bucket.contains('velo') ) {
            map.jobNameRunAutomateTest = determineJobNameRunAutomateTest(script.env.BRANCH_NAME)
        }

        // map.sonarqubeURL = determineSonarqubeURL()
        // map.sonarqubeVerifyTimeout = determineSonarqubeVerifyTimeout()
        map.sonarqubeSkip = determineSonarqubeSkip()

        util.debug "Calculating deployment information ..."
        map.environments = determineEnvironment(map.gitBranch)

        // Determine list of environment into map of deployment related variable
        map.kubernetesContexts = []
        for (environment in map.environments) {
            Map item = [:]

            item.environment = environment
            item.kubernetesContext = determineKubernetesContext(environment)
            item.kubernetesPodReplicas = determineKubernetesPodReplicas(environment)
            item.kubernetesNamespace = determineKubernetesNamespace(environment)
            item.kubernetesPodReplicas = determineKubernetesPodReplicas(environment)
            item.kubernetesIngressClass = determineKubernetesIngressClass(environment)
            item.kubernetesIngressHostname = determineKubernetesIngressHostname(environment)

            map.kubernetesContexts.add(item)
        }

        map.previousStage = []
        map.previousStage.add(script.env.STAGE_NAME)

        util.info ("""
Jenkin configurations: 
${prettyPrint(toJson(config))}
---------------------------------------------------------------
Assembled contexts: 
${prettyPrint(toJson(map))}
        """)
    }

    // Get Jenkins configurations
    boolean getDebug() {
        return config.debug
    }

    String getProjectName() {
        return config.projectName
    }

    String getSubgroup() {
        return config.registry.subgroup
    }

    String getRegistryUrl() {
        return config.registry.url
    }

    String getPipelineType() {
        return config.pipelineType
    }


    String getRegistryCredential() {
        return config.registry.registryCredential
    }

    String getS3Bucket() {
        return config.s3Bucket
    }

    String getConfigRepoURL() {
        return config.configRepo.configRepoURL
    }

    String getConfigRepoCredential() {
        return config.configRepo.configRepoCredential
    }

    String getJenkinsAgentNode() {
        if (config.jenkinsConfig instanceof NullObject) {
            return null
        } else {
            return config.jenkinsConfig.agent
        }
    }

    String getGitURL() {
        return map.gitUrl
    }

    String getBuildURL() {
        return map.buildUrl
    }

    // Get Assembled contexts
    String getJobName() {
        return map.jobName
    }

    String getEnvironment() {
        return map.environment
    }

    String getTagVersion() {
        return map.tagVersion
    }

    String getGitBranch() {
        return map.gitBranch
    }

    String getGitBranchName() {
        return map.gitBranchName
    }

    String getBuildNumber() {
        return map.buildNumber
    }

    String getContainerName() {
        if(config.registry.subgroup) {
            return "${config.registry.url}/${config.registry.group}/${config.registry.subgroup}/${config.registry.project}"
        }
        return "${config.registry.url}/${config.registry.group}/${config.registry.project}"
    }

    List getKubernetesContexts() {
        return map.kubernetesContexts
    }

    // Get list of previous stage
    List getPreviousStage() {
        return map.previousStage
    }

    // Add finished stage to list of previous stage
    String addPreviousStage(stage) {
        return map.previousStage.add(stage)
    }

    boolean getCiSkip() {
        return map.gitCiSkip
    }

    String getJobNameRunAutomateTest() {
        return map.jobNameRunAutomateTest
    }

    String getSonarqubeURL() {
        return config.sonarqubeConfig.url
    }

    String getSonarqubeVerifyTimeout() {
        return config.sonarqubeConfig.verifyTimeoutSecond
    }

    String getSonarqubeSkip(){
        return map.sonarqubeSkip
    }

    String getBusinessUnit() {
        return config.businessUnit
    }

    // String getS3BucketDeploymentArtifact() {
    //     return config.s3BucketDeploymentArtifact
    // }

    private String retrieveGitCommit() {
        return script.sh(
            returnStdout: true, script: 'git rev-parse HEAD'
        ).trim()
    }

    private String retrieveGitTag() {
        return script.sh(
            returnStdout: true, script: 'git name-rev --name-only --tags HEAD'
        ).trim()
    }

    private String retrieveGitCommitAuthor() {
        return script.sh(
            returnStdout: true, script: "git --no-pager show -s --format='%an (%ae)' HEAD"
        ).trim()
    }

    private String retrieveGitCommitMessage() {
        return script.sh(
            returnStdout: true, script: "git log -1 --pretty=%B HEAD"
        ).trim()
    }

    private String retrieveGitCommitTime() {
        return script.sh(
            returnStdout: true, script: "git show -s --format=%ci HEAD"
        ).trim()
    }

    private String retrieveGitUrl() {
        script.sh(
            returnStdout: true, script: 'git config --get remote.origin.url'
        ).trim()
    }

    private String determineTagVersion() {
        if (retrieveGitTag() != 'undefined') {
            return retrieveGitTag()
        } else {
            return "${map.buildNumber}-${map.gitCommit.take(8)}"
        }
    }

    private boolean retrieveGitCiSkip() {
        script.sh(
            returnStdout: true, label: "Get [ci-skip] from commit", script: 'git show --pretty=%s%b -s'
        ).toLowerCase().contains('[ci-skip]')
    }

    // Given a branch like "feature/hello-world", it extracts
    // "feature-hello-world" from it.
    // to avoid failure when using branch name in shell script
    static private String extractBranchCode(branch) {
        if (branch.contains("/")) {
            return branch.replace("/", "-")
        } else {
            branch
        }
    }

    private String determineEnvironment(branch) {
        // Specific branch map to specific environment
        def env = config.branchToEnvironmentMapping[branch]
        if (env) {
            return env
        }

        // Loop needs to be done like this due to
        // https://issues.jenkins-ci.org/browse/JENKINS-27421 and
        // https://issues.jenkins-ci.org/browse/JENKINS-35191.
        for (String key : config.branchToEnvironmentMapping.keySet()) {
            if (map.gitBranch.startsWith(key)) {
                return config.branchToEnvironmentMapping[key]
            }
        }

        // Any branch
        def genericEnv = config.branchToEnvironmentMapping["*"]
        if (genericEnv) {
            return genericEnv
        }

        return util.info(
            """
            ----------------------------------------------
            No environment to deploy to was determined
            [gitBranch=${map.gitBranch}, projectId=${config.projectName}]
            """
        )
    }

    private String determineKubernetesContext(String environment) {
        if (config.kubernetes.environmentToContextMapping) {
            return config.kubernetes.environmentToContextMapping[environment]
        }
        return null
    }

    private String determineKubernetesPodReplicas(String environment) {
        if (config.kubernetes.environmentToPodReplicasMapping) {
            return config.kubernetes.environmentToPodReplicasMapping[environment]
        }
        return null
    }

    private String determineKubernetesNamespace(String environment) {
        if (config.kubernetes.environmentToNamespaceMapping) {
            return config.kubernetes.environmentToNamespaceMapping[environment]
        }
        return null
    }

    private String determineKubernetesPodReplicas(String environment) {
        if (config.kubernetes.environmentToPodReplicasMapping) {
            return config.kubernetes.environmentToPodReplicasMapping[environment]
        }
        return null
    }

    private String determineKubernetesIngressClass(String environment) {
        if (config.kubernetes.environmentToIngressClassMapping) {
            return config.kubernetes.environmentToIngressClassMapping[environment]
        }
        return null
    }

    private String determineKubernetesIngressHostname(String environment) {
        if (config.kubernetes.environmentToIngressHostnameMapping) {
            return config.kubernetes.environmentToIngressHostnameMapping[environment]
        }
        return null
    }

    private String determineJobNameRunAutomateTest(String branch){
        if ( branch =~ /(release)/ ) {
            branch = "release"
        }
        if ( config.runAutomateTest[branch] ) {
            return config.runAutomateTest[branch]
        }
        return null
    }

    private String determineSonarqubeSkip(){
        if ( config.sonarqubeConfig.skip ) {
            util.info """
                ----------------------------------------------
                Sonarqube scanner skip because ${config.sonarqubeConfig.skip}
            """
            return "true"
        }
        return "false"
    }

}
