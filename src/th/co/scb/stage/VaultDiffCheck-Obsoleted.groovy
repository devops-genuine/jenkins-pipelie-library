package th.co.scb.stage

import th.co.scb.PipelineContext
// import groovy.json.JsonSlurperClassic

class VaultDiffCheck {

    def script
    String pipelineType
    PipelineContext context

    VaultDiffCheck(script, PipelineContext context, String pipelineType) {
        this.script = script
        this.context = context
        this.pipelineType = pipelineType
    }
    
    def execute() {
        script.stage('Pull App Config') {
            // THIS STAGE CANNOT IMPORT ANY LIBRARY, I WILL CHANGE ALL LOGIC TO jenkins-pipeline-library/vars/stageVaultDiffCheck.groovy
        }
    }
}
