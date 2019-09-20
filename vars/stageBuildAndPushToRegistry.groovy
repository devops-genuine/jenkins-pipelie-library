#!/usr/bin/env groovy

import th.co.scb.PipelineContext
import th.co.scb.stage.BuildAndPushToRegistry

def call(PipelineContext context) {
    BuildAndPushToRegistry build = new BuildAndPushToRegistry(this, context)
    build.execute()
}

return this
