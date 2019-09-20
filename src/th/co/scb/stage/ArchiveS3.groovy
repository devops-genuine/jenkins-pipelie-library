package th.co.scb.stage

import th.co.scb.PipelineContext

class ArchiveS3 {

    def script
    PipelineContext context
    Map kubernetesContext
    String file

    ArchiveS3(script, PipelineContext context, kubernetesContext) {
        this.script = script
        this.context = context
        this.kubernetesContext = kubernetesContext
        this.file = "deployment-${context.projectName}-${context.gitBranchName}-${context.tagVersion}.yaml"
    }

    def execute() {
        script.stage('Archive S3') {
            String currentResult = null

            try {
                if (context.ciSkip) {
                    context.util.info "${script.env.STAGE_NAME} stage skipped due to git commit override"
                    currentResult = 'SKIPPED'
                }

                if (!context.previousStage.contains('Deploy')) {
                    context.util.error "must run 'Deploy' stage before this stage"
                    currentResult = 'FAILURE'
                } else {
                    context.util.info "Running ${script.env.STAGE_NAME}..."

                    script.s3Upload(
                        file: file,
                        bucket: context.s3Bucket,
                        path: "${context.projectName}/${kubernetesContext['environment']}/${file}",
                    )
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
                        break
                    case 'UNSTABLE':
                        context.util.gitlabCommitStatus.failed(script.env.STAGE_NAME)
                        script.currentBuild.result = currentResult
                        break
                    case 'FAILURE':
                        context.util.gitlabCommitStatus.failed(script.env.STAGE_NAME)
                        script.currentBuild.result = currentResult
                        context.util.slack.notify(context, currentResult, script.env.STAGE_NAME)
                        break
                }

                context.addPreviousStage(script.env.STAGE_NAME)
            }
        }
    }
}

