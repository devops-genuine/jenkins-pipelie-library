#!/usr/bin/env groovy

import th.co.scb.PipelineContext
import th.co.scb.stage.QualityGateFromSonarqube

def call(PipelineContext context) {
    QualityGateFromSonarqube qualityGateFromSonarqube = new QualityGateFromSonarqube(this, context, context.pipelineType)
    qualityGateFromSonarqube.execute()
}

return this
