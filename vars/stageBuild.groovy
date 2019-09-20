#!/usr/bin/env groovy

import th.co.scb.PipelineContext
import th.co.scb.stage.Build

def call(PipelineContext context) {
    Build build = new Build(this, context)
    build.execute()
}

return this
