#!/usr/bin/env groovy

import th.co.scb.PipelineContext
import th.co.scb.stage.BuildWithSonarqube

def call(PipelineContext context) {
    BuildWithSonarqube buildWithSonarqube = new BuildWithSonarqube(this, context, context.pipelineType)
    buildWithSonarqube.execute()
}

return this
