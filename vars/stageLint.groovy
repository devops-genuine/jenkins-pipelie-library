#!/usr/bin/env groovy

import th.co.scb.PipelineContext
import th.co.scb.stage.Lint

def call(PipelineContext context) {
    Lint lint = new Lint(this, context, context.pipelineType)
    lint.execute()
}

return this
