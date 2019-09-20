package th.co.scb.stage

import th.co.scb.PipelineContext

class QualityGateFromSonarqube {

    def script
    String pipelineType
    PipelineContext context

    QualityGateFromSonarqube(script, PipelineContext context, String pipelineType) {
        this.script = script
        this.context = context
        this.pipelineType = pipelineType
    }

    def execute() {     
        script.stage('Quality Gate From Sonarqube') {
            String currentResult = null
            Integer pipelineStatusCode = 0
            def qualityGate

            try {
                if ( context.ciSkip ) {
                    context.util.info "${script.env.STAGE_NAME} stage skipped due to git commit override"
                    currentResult = 'SKIPPED'
                }
                if ( !context.previousStage.contains('Code Analysis by Sonarqube') ) {
                    context.util.error "must run 'Code Analysis by Sonarqube' stage before this stage"
                    currentResult = 'FAILURE'
                } else if ( context.sonarqubeSkip == "true" ) {
                    currentResult = 'SKIPPED'
                    context.util.slack.notify(context, currentResult, script.env.STAGE_NAME)
                } else {
                    context.util.info "Running ${script.env.STAGE_NAME}..."
                    script.timeout(time: 1, unit: 'MINUTES') {
                        script.sleep context.sonarqubeVerifyTimeout
                        qualityGate = script.waitForQualityGate(false)
                        if ( qualityGate.status == 'PENDING' || qualityGate.status == 'IN_PROGRESS' ) {
                            context.util.info "Sonarqube Quality Code is ${qualityGate.status}, Please contact DevOps team for increase verifyTimeoutSecond in your Jenkinsfile."
                            currentResult = 'FAILURE'
                            return false
                        } else if ( qualityGate.status != 'OK' ) {
                            currentResult = 'FAILURE'
                            return false
                        } else {
                            currentResult = 'SUCCESS'
                            return true
                        }
                    }
                    context.util.info "Sonarqube Quality Code is ${qualityGate.status}"
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
