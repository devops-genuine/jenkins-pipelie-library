package th.co.scb.stage

import th.co.scb.PipelineContext

class Deploy {

    def script
    PipelineContext context
    String file
    String deployToK8SContext
    String targetEnvName
    String targetNamespace
    Boolean shouldArchive
    //Map kubernetesContext

    Deploy(script, PipelineContext context) {
        this.script = script
        this.context = context
        this.shouldArchive = false
        //this.file = "deployment-${context.projectName}-staging-${context.tagVersion}.yaml"
        //this.kubernetesContext = kubernetesContext
    }

    def execute() {
        script.stage('Deployment') {
            String currentResult = null   
            Integer pipelineStatusCode         
            
            try {
                if (context.ciSkip) {
                    context.util.info "${script.env.STAGE_NAME} stage skipped due to git commit override"
                    currentResult = 'SKIPPED'
                }

                if (!(context.gitBranchName =~ /(master|develop|release)/)) {
                    context.util.skipPipeline(script.env.STAGE_NAME)
                    currentResult = 'SKIPPED'
                } else {

                    if (!context.previousStage.contains('Build K8S Manifests')) {
                        context.util.error "must run 'Build K8S manifests' stage before this stage"
                        currentResult = 'FAILURE'
                    } else {

                        for (Map kubeContext in context.kubernetesContexts) {
                            if(kubeContext['environment'] != "production"){
                                deployToK8SContext = kubeContext['kubernetesContext']
                                targetEnvName = kubeContext['environment']
                                targetNamespace = kubeContext['kubernetesNamespace']
                            }
                        }                    

                        file = "deployment-${context.projectName}-${targetEnvName}-${context.tagVersion}.yaml"

                        context.util.info "Running ${script.env.STAGE_NAME}..."

                        context.util.info "switch to ${deployToK8SContext}"

                        // ----------------- Apply ConfigMap -----------------
                        def commandConfigmapPrefix = "kubectl create configmap ${context.projectName}-config --dry-run=true -o yaml "
                        def commandConfigmapSuffix = "-n ${targetNamespace} | kubectl apply -f -"
                        def commandConfigmapFromLiteral = ""
                        def commandLabelConfigmap= "kubectl -n ${targetNamespace} label --overwrite configmap ${context.projectName}-config config-version=${context.tagVersion}"

                        // def fileConfig = script.sh (
                        //     returnStdout: true, script: """
                        //         echo \$(ls ${script.env.WORKSPACE}/app-configs/configuration/${targetEnvName})
                        //     """
                        // ).trim()
                        // def listfileConfig = []
                        // listfileConfig = fileConfig.tokenize(' ')
                        // def listConfigKey = []
                        def envArray = new String[3]
                        envArray[0] = "develop"
                        envArray[1] = "test"
                        envArray[2] = "staging"

                        def fileConfigKeyExist = false
                        def filePath = ""

                        if ( context.gitBranchName =~ /(develop)/ ) {

                            filePath = "${script.env.WORKSPACE}/pipeline-app-configs/${envArray[0]}/${context.projectName}/configuration/app.properties"
                            fileConfigKeyExist = script.fileExists(filePath)

                        }else if(context.gitBranchName =~ /(release)/ ){

                            filePath = "${script.env.WORKSPACE}/pipeline-app-configs/${envArray[1]}/${context.projectName}/configuration/app.properties"
                            fileConfigKeyExist = script.fileExists(filePath)

                        }else{

                            filePath = "${script.env.WORKSPACE}/pipeline-app-configs/${envArray[2]}/${context.projectName}/configuration/app.properties"
                            fileConfigKeyExist = script.fileExists(filePath)
                        }
                        

                        def configInProperties
                        def configInPropertiesKey = []
                        def i
                        def value
                        if ( fileConfigKeyExist == false ) {
                            context.util.info "File config in ${targetEnvName} does not exist. No need to apply configmap."
                        } else {
                            configInProperties = script.readProperties(file: filePath);
                            configInPropertiesKey = configInProperties.keySet()
                            i = 0
                            for ( configKey in configInPropertiesKey ) {
                                context.util.info "\t${i + 1}. ${configKey}"
                                value = configInProperties[configKey]
                                commandConfigmapFromLiteral = "--from-literal=${configKey}='${value}'" + " " + commandConfigmapFromLiteral
                                i++
                            }
                            // for ( fileNameConfig in listfileConfig ) {

                            //     configInProperties = script.readProperties(file: filePath)
                            //     configInPropertiesKey = configInProperties.keySet()
                            //     for ( keyInProperties in configInPropertiesKey ) {
                            //         listConfigKey.add(keyInProperties)
                            //         value = configInProperties[keyInProperties]
                            //         commandConfigmapFromLiteral = "--from-literal=${keyInProperties}='${value}'" + " " + commandConfigmapFromLiteral
                            //     }
                            // }
                            
                            def commandConfigmap = commandConfigmapPrefix + commandConfigmapFromLiteral + commandConfigmapSuffix
                            context.util.info "Command create configmap is ${commandConfigmap}"
                            // EXAMPLE. kubectl create configmap application-poc-config --dry-run=true -o yaml --from-literal=test_1='test_1_data' --from-file=test-1 -n dev-velo-core | kubectl apply -f -

                            pipelineStatusCode = script.sh(
                                returnStatus: true, label: "deploy to kubernetes", script: """
                                    kubectl config use-context ${deployToK8SContext}
                                    ${commandConfigmap}
                                    ${commandLabelConfigmap}
                                """
                            )
                        }

                        // ----------------- Apply Deployment.yaml -----------------
                        pipelineStatusCode = script.sh(
                            returnStatus: true, label: "deploy to kubernetes", script: """
                                kubectl config use-context ${deployToK8SContext}

                                echo "Deploy to Cluster"
                                kubectl get nodes
                                cat ${file}
                                kubectl apply -f ${file} -n ${targetNamespace}
                            """
                        )

                        if (pipelineStatusCode != 0) {
                            currentResult = 'FAILURE'
                            context.util.error "${script.env.STAGE_NAME} "
                        } else {
                            currentResult = 'SUCCESS'
                            shouldArchive = true
                        }
                    }
                }

            } catch (Exception err) {
                currentResult = 'FAILURE'
                throw err
            } finally {
                switch (currentResult) {
                    case 'SKIPPED':
                        break
                    case 'SUCCESS':
                        context.util.gitlabCommitStatus.success(script.env.STAGE_NAME)
                        script.currentBuild.result = currentResult
                        // archiveArtifact()
                        break
                    case 'UNSTABLE':
                        context.util.gitlabCommitStatus.failed(script.env.STAGE_NAME)
                        script.currentBuild.result = currentResult
                        script.error('Unstable')
                        break
                    case 'FAILURE':
                        context.util.gitlabCommitStatus.failed(script.env.STAGE_NAME)
                        script.currentBuild.result = currentResult
                        context.util.slack.notify(context, currentResult, script.env.STAGE_NAME)
                        script.error('Failed')
                        break
                }

                context.addPreviousStage(script.env.STAGE_NAME)
            }
        }
    }

    private void archiveArtifact() {
        if (shouldArchive) {
            script.archiveArtifacts artifacts: file
        }
    }

}
