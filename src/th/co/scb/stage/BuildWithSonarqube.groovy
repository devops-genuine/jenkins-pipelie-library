package th.co.scb.stage

import th.co.scb.PipelineContext

class BuildWithSonarqube {

    def script
    String pipelineType
    PipelineContext context

    BuildWithSonarqube(script, PipelineContext context, String pipelineType) {
        this.script = script
        this.context = context
        this.pipelineType = pipelineType
    }

    def execute() {          
        script.stage('Code Analysis by Sonarqube') {
            String currentResult = null
            Integer pipelineStatusCode = 0

            try {
                if ( context.ciSkip ) {
                    context.util.info "${script.env.STAGE_NAME} stage skipped due to git commit override"
                    currentResult = 'SKIPPED'
                }

                if ( !context.previousStage.contains('Unit test') ) {
                    context.util.error "must run 'Unit Test' stage before this stage"
                    currentResult = 'FAILURE'
                } else if ( context.sonarqubeSkip == "true" ) {
                    currentResult = 'SKIPPED'
                    context.util.slack.notify(context, currentResult, script.env.STAGE_NAME)
                } else {
                    context.util.info "Running ${script.env.STAGE_NAME}..."
                    // Replace 2 variables in Makefile
                    pipelineStatusCode = script.sh(
                        returnStatus: true, label: "Replace Makefile for sonarqube", script: """
                            sed -i s~#SONARQUBE_URL#~${context.sonarqubeURL}~g Makefile
                            sed -i s~#APP_VERSION#~${context.gitBranchName}-${context.tagVersion}~g Makefile
                        """
                    )

                    if ( pipelineStatusCode != 0 ) {
                        currentResult = 'FAILURE'
                        throw err
                    }

                    script.withSonarQubeEnv('sonarqube') {
                        pipelineStatusCode = script.sh (
                            returnStatus: true, script: """
                                make ci_sonarqube
                            """
                        )
                        if ( pipelineStatusCode != 0 ) {
                            currentResult = 'FAILURE'
                        } else {
                            currentResult = 'SUCCESS'
                        }
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
                        context.util.slack.notify(context, currentResult, script.env.STAGE_NAME)
                        script.error('Failed')
                        break
                }
                context.addPreviousStage(script.env.STAGE_NAME)
            }
        }
    }
}
