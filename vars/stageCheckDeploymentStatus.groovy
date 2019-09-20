#!/usr/bin/env groovy

import th.co.scb.PipelineContext
import th.co.scb.stage.CheckDeploymentStatus

def call(PipelineContext context) {
    CheckDeploymentStatus checkDeploymentStatus = new CheckDeploymentStatus(this, context)
    checkDeploymentStatus.execute()
}

return this
