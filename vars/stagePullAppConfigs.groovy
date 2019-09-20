#!/usr/bin/env groovy

import th.co.scb.PipelineContext
import th.co.scb.stage.PullAppConfigs

def call(PipelineContext context) {
    PullAppConfigs pullAppConfigs = new PullAppConfigs(this, context)
    pullAppConfigs.execute()
}

return this