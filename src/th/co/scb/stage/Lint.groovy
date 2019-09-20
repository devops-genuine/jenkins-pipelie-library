package th.co.scb.stage

import th.co.scb.PipelineContext

class Lint {

    def script
    String pipelineType
    PipelineContext context

    Lint(script, PipelineContext context, String pipelineType) {
        this.script = script
        this.context = context
        this.pipelineType = pipelineType
    }

    def execute() {
        script.stage('Lint') {
            String currentResult = null

            try {
                if (context.ciSkip) {
                    context.util.info "${script.env.STAGE_NAME} stage skipped due to git commit override"
                    currentResult = 'SKIPPED'
                }

                if (!context.previousStage.contains('Build')) {
                    context.util.error "must run 'Build' stage before this stage"
                    currentResult = 'FAILURE'
                } else {
                    context.util.info "Running ${script.env.STAGE_NAME}..."
                    currentResult = lint(pipelineType)
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

    private String lint(pipelineType) {
        // Default value should indicate unsuccessful
        Integer pipelineStatusCode = 1

        // TODO: Find elegant way to support default case where pipelineType not support
        switch (pipelineType) {
            case 'golang':
                pipelineStatusCode = script.sh(
                    returnStatus: true, script: """
                        docker run --rm ${context.containerName}:${context.gitBranchName}-${context.tagVersion} sh -c 'make lint'
                    """
                )
                break
            case 'javascript':
                pipelineStatusCode = script.sh(
                    returnStatus: true, script: """
                        docker run --rm ${context.containerName}:${context.gitBranchName}-${context.tagVersion} sh -c 'yarn lint'
                    """
                )
                break
        }

        // Status 0 indicate successful, status 1 indicate unsuccessful
        if (pipelineStatusCode != 0) {
            return 'FAILED'
        } else {
            return 'SUCCESS'
        }
    }
}
