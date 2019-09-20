#!/usr/bin/env groovy

import th.co.scb.Pipeline
import th.co.scb.PipelineUtility

def call(Map config, Closure body) {
    PipelineUtility utility = new PipelineUtility(this, config.utility)
    Pipeline pipeline = new Pipeline(this, config, utility)
    return pipeline.execute(body)
}

return this
