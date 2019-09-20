package th.co.scb.stage

import th.co.scb.PipelineContext

class RunAutomateTest {

    def script
    String pipelineType
    PipelineContext context
    String jobName
    String CRUMB
    String latest_status_jenkins_automate_test_build
    String delayRunTestInJobRunAutomateTest = "10"
    String username = "automate_test"
    String password = "Welcome1"
    String tokenInJobRunAutomateTest = "automate_test"

    RunAutomateTest(script, PipelineContext context, String pipelineType) {
        this.script = script
        this.context = context
        this.pipelineType = pipelineType
    }

    def execute() {
        script.stage('Run Automate Test') {
            String currentResult = null
            Integer pipelineStatusCode = 0

            try {
                if (context.ciSkip) {
                    context.util.info "${script.env.STAGE_NAME} stage skipped due to git commit override"
                    currentResult = 'SKIPPED'
                }
                //if ( !(context.gitBranch =~ /(develop)/) ) {
                if ( 1 == 1 ) {
                    context.util.skipPipeline(script.env.STAGE_NAME)
                    currentResult = 'SKIPPED'
                } else {
                    if ( !context.previousStage.contains('Deployment') ) {
                        context.util.error "must run 'Deployment' stage before this stage"
                        currentResult = 'FAILURE'
                    } 
                    else if ( script.env.JOB_NAME.contains('application-poc') ) {
                        context.util.skipPipeline(script.env.STAGE_NAME)
                        currentResult = 'SKIPPED'
                    } else {
                        jobName = context.jobNameRunAutomateTest
                        context.util.info "Running ${script.env.STAGE_NAME}..."

                        CRUMB = script.sh (
                            returnStdout: true, script: """
                                wget -q --auth-no-challenge --user ${username} --password ${password} --output-document - 'http://jenkins-master.velo-nonprod.com:8080/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,":",//crumb)'
                            """
                        )
                        latest_status_jenkins_automate_test_build = script.sh (
                            returnStdout: true, script: """
                                wget -q --auth-no-challenge --user ${username} --password ${password} --output-document - 'http://jenkins-master.velo-nonprod.com:8080/job/${jobName}/lastBuild/api/json' |jq -r '.result'
                            """
                        )
                        CRUMB = CRUMB.trim()
                        latest_status_jenkins_automate_test_build = latest_status_jenkins_automate_test_build.trim()
                        if ( latest_status_jenkins_automate_test_build == "null" ) {
                            context.util.info "Job testing is running."
                            context.util.slack.notify(context, "RUN_AUTOMATE_TEST_IGNORE", script.env.STAGE_NAME)
                        } else {
                            context.util.info "Job testing is not running, can start run job-testing."
                            context.util.slack.notify(context, "RUN_AUTOMATE_TEST_START", script.env.STAGE_NAME)
                            pipelineStatusCode = script.sh (
                                returnStatus: true, script: """
                                    curl -X POST --user ${username}:${password} -H ${CRUMB} --data-urlencode json='{"parameter": [{"name":"JOB_CALLER_NAME", "value":"${script.env.JOB_NAME}"}, {"name":"JOB_CALLER_NUMBER", "value":"${script.env.BUILD_NUMBER}"}, {"name":"DELAY_BUILD", "value":"${delayRunTestInJobRunAutomateTest}"}]}' http://jenkins-master.velo-nonprod.com:8080/job/${jobName}/build?token=${tokenInJobRunAutomateTest}
                                """
                            )
                        }
                        if ( pipelineStatusCode != 0 ) {
                            currentResult = 'FAILURE'
                        } else {
                            currentResult = "SUCCESS"
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
