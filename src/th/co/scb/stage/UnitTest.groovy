package th.co.scb.stage

import th.co.scb.PipelineContext

/* Prerequisite to use UnitTest pipeline
*  -------------------------------------
*  Jenkins must have following plugin:
*  - junit
*  - cobertura
*
*  Jenkins condition must be following:
*  - Docker installed
*
*  Supported Language:
*  - Golang:
*       - makefile must have `make ci_test`
*       - Dockerfile must have following golang packages
*           - golint
*           - go-junit-report
*           - gocov
*           - gocov-xml
*  - Javascript:
*       - package.json must have `yarn test`
*/

class UnitTest {

    def script
    String pipelineType
    PipelineContext context

    UnitTest(script, PipelineContext context, String pipelineType) {
        this.script = script
        this.context = context
        this.pipelineType = pipelineType
    }

    def execute() {
        script.stage('Unit test') {
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
                    currentResult = unitTest(pipelineType)
                }
            } catch (err) {
                currentResult = 'FAILURE'
                throw err
            } finally {
                switch (currentResult) {
                    case 'SKIPPED':
                        context.util.gitlabCommitStatus.skipped(script.env.STAGE_NAME)
                        break
                    case 'SUCCESS':
                        context.util.gitlabCommitStatus.success(script.env.STAGE_NAME)
                        script.currentBuild.result = currentResult

                        if (pipelineType == 'golang') {
                            script.junit 'reports/coverage-tasks.xml'
                            script.cobertura coberturaReportFile: 'reports/coverage.xml'
                        }

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

    private String unitTest(pipelineType) {
        // Default value should indicate unsuccessful
        Integer pipelineStatusCode = 1

        // TODO: Find elegant way to support default case where pipelineType not support
        switch (pipelineType) {
            case 'golang':
                pipelineStatusCode = script.sh(
                    returnStatus: true, label: "unit test", script: """
                        mkdir -p \$(pwd)/reports
                        docker run --rm -v \$(pwd)/reports:/reports ${context.containerName}:${context.gitBranchName}-${context.tagVersion} sh -c 'make ci_test | go-junit-report > /reports/coverage-tasks.xml'
                        docker run --rm -v \$(pwd)/reports:/reports ${context.containerName}:${context.gitBranchName}-${context.tagVersion} sh -c 'make ci_test; gocov convert .coverage.txt | gocov-xml > /reports/coverage.xml && cp .coverage.txt /reports/'
                        /usr/bin/sudo chown -R jenkins:jenkins \$(pwd)/reports
                    """
                )
                break
            case 'javascript':
                pipelineStatusCode = script.sh(
                    returnStatus: true, label: "unit test", script: """
                        docker run --rm ${context.containerName}:${context.gitBranchName}-${context.tagVersion} sh -c 'yarn test'
                    """
                )
                break
            case 'vuejs':
                pipelineStatusCode = script.sh(
                    returnStatus: true, label: "unit test", script: """
                        docker run -v \$(pwd)/reports:/app/coverage -v \$(pwd)/tests:/app/tests ${context.containerName}:${context.gitBranchName}-${context.tagVersion} sh -c "yarn test || true"
                        sudo chown -R jenkins:jenkins reports/
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
