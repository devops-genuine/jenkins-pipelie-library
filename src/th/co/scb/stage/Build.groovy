package th.co.scb.stage

import th.co.scb.PipelineContext

class Build {

    def script
    PipelineContext context
    

    Build(script, PipelineContext context) {
        this.script = script
        this.context = context
    }

    def execute() {
        script.stage('Build') {
            String currentResult = null

            try {
                if (context.ciSkip) {
                    context.util.info "${script.env.STAGE_NAME} stage skipped due to git commit override"
                    currentResult = 'SKIPPED'
                }

                if ( !context.previousStage.contains('Vault Diff Check') ) {
                    context.util.error "must run 'Vault Diff Check' stage before this stage"
                    currentResult = 'FAILURE'
                } else {
                    context.util.info "Running ${script.env.STAGE_NAME}..."

                    Integer pipelineStatusCode = 0

                    if ( context.s3Bucket.contains('velo') && context.projectName.contains('dashboard') ) {
                        pipelineStatusCode = script.sh(
                            returnStatus: true, label: "build image", script: """
                                docker build --pull -t ${context.containerName}:${context.gitBranchName}-${context.tagVersion} .
                            """
                        )
                    } else {
                        pipelineStatusCode = script.sh(
                            returnStatus: true, label: "build image", script: """
                                docker build --target builder -t ${context.containerName}:${context.gitBranchName}-${context.tagVersion} .
                            """
                        )
                    }

                    if (pipelineStatusCode != 0) {
                        currentResult = 'FAILURE'
                    } else {
                        currentResult = 'SUCCESS'
                    }
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
                        context.util.slack.notify(context, currentResult, script.env.stage_name)
                        script.error('Failed')
                        break
                }

                context.addPreviousStage(script.env.STAGE_NAME)
            }
        }
    }
}
