package th.co.scb.stage

import th.co.scb.PipelineContext

class PullAppConfigs {

    def script
    PipelineContext context

    PullAppConfigs(script, PipelineContext context) {
        this.script = script
        this.context = context
    }

    def execute() {          
        script.stage('Pull App Config') {

            String currentResult = null

            try {
                if ( context.ciSkip ) {
                    context.util.info "${script.env.STAGE_NAME} stage skipped due to git commit override"
                    currentResult = 'SKIPPED'
                }

                if (!(context.gitBranchName =~ /(master|develop|release)/)) {
                    context.util.skipPipeline(script.env.STAGE_NAME)
                    currentResult = 'SKIPPED'
                } else {
                    context.util.info "Running ${script.env.STAGE_NAME}..."
                    script.sh "rm -rf pipeline-app-configs && mkdir -p pipeline-app-configs && cd pipeline-app-configs"
                    script.checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'pipeline-app-configs']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: "${context.configRepoCredential}", url: "${context.configRepoURL}"]]])
                    //script.git branch: 'master', credentialsId: "${context.configRepoCredential}", url: "${context.configRepoURL}"
                    //script.sh "ls -la"
                    script.sh "ls -la pipeline-app-configs"
                }
            } catch (err) {
                currentResult = 'FAILURE'
                throw err
            } finally {
                switch (currentResult) {
                    case 'SKIPPED':
                        break
                    case 'SUCCESS':
                        context.util.gitlabCommitStatus.success(script.env.STAGE_NAME)
                        script.currentBuild.result = currentResult
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
}
