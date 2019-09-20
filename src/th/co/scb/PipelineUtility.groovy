#!/usr/bin/env groovy
package th.co.scb

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

class PipelineUtility {
    // https://github.com/jenkinsci/pipeline-examples/blob/master/pipeline-examples/slacknotify/slackNotify.groovy

    GitlabCommitStatus gitlabCommitStatus
    SlackNotify slack

    private def script
    private boolean debug

    PipelineUtility(script, util) {
        this.script = script
        this.gitlabCommitStatus = new GitlabCommitStatus(script, util)
        this.slack = new SlackNotify(script, util)
        this.debug = util.debug ?: false
    }

    void debug(String message) {
        if (debug) {
            script.echo message
        }
    }

    void info(String message) {
        script.echo message
    }

    void error(String message) {
        script.error message
    }

    static void skipPipeline(stageName) {
        Utils.markStageSkippedForConditional("${stageName}")
    }

}

class GitlabCommitStatus {

    private Object script
    private Boolean gitlabPlugin

    GitlabCommitStatus(script, util) {
        this.script = script
        this.gitlabPlugin = util.gitlabPlugin
    }

    void skipped(stageName) {
        if (gitlabPlugin) {
            script.updateGitlabCommitStatus(name: stageName, state: 'canceled')
        }
    }

    void failed(stageName) {
        if (gitlabPlugin) {
            script.updateGitlabCommitStatus(name: stageName, state: 'failed')
        }
    }

    void success(stageName) {
        if (gitlabPlugin) {
            script.updateGitlabCommitStatus(name: stageName, state: 'success')
        }
    }
}

class SlackNotify {

    def Script
    def slackPlugin

    SlackNotify(script, util) {
        this.script = script
        this.slackPlugin = util.slackPlugin
    }

    void notify(Context context, String buildStatus, def stage = null) {
        if (slackPlugin) {
            List jobNameParts = context.jobName.tokenize('/')
            String colorCode = getColorCode(buildStatus)

            String repository = "Repository: - <${context.gitURL}/tree/${context.gitBranch}|${jobNameParts[0]}> on Branch *${context.gitBranchName}*"
            String result = "Result: <${context.buildURL}console|Console Output>"
            String subject = getSubject(context, buildStatus, stage, jobNameParts)
            String summary = "${subject}\n${repository}\n${result}"
            
            script.slackSend(
                baseUrl: slackPlugin.baseUrl,
                channel: slackPlugin.channel,
                teamDomain: slackPlugin.teamDomain,
                tokenCredentialId: slackPlugin.tokenCredentialId,
                color: colorCode,
                message: summary
            )
        }
    }

    private static String getSubject(Context context, String buildStatus, def stage, List jobNameParts) {
        String blueOceanURL = context.buildURL.replace("job/${jobNameParts[0]}/job", "blue/organizations/jenkins/${jobNameParts[0]}/detail")

        if ( buildStatus.contains('RUN_AUTOMATE_TEST') ) {
            if ( buildStatus.contains('IGNORE') ) {
                return "*${buildStatus}* because job testing is running, <${blueOceanURL}|${context.jobName}#${context.buildNumber}>"
            } else if ( buildStatus.contains('START') ) {
                return "*${buildStatus}*, <${blueOceanURL}|${context.jobName}#${context.buildNumber}>"
            }
        }

        if (buildStatus == 'FAILURE') {
            return "*${buildStatus}:* - <${blueOceanURL}|${context.jobName}#${context.buildNumber}> on Stage *${stage}*"
        } else {
            return "*${buildStatus}:* - <${blueOceanURL}|${context.jobName}#${context.buildNumber}>"
        }
    }

    private static String getColorCode(String buildStatus) {
        switch (buildStatus) {
            case 'STARTED':
                return '#0088FF'
            case 'SUCCESS':
                return '#2EB886'
            case 'FAILURE':
                return '#FF0000'
            case 'UNSTABLE':
                return '#DAA038'
            case 'ABORTED':
                return '#9b9b9b'
            case 'RUN_AUTOMATE_TEST_START':
                return '#0088FF'
            case 'RUN_AUTOMATE_TEST_IGNORE':
                return '#9b9b9b'
            default:
                return '#FF0000'
        }
    }
}

