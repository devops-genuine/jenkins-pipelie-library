#!/usr/bin/env groovy

import th.co.scb.PipelineContext
import th.co.scb.stage.Deploy


def call(PipelineContext context) {
    Deploy deploy = new Deploy(this, context)
    deploy.execute()
}

return this
