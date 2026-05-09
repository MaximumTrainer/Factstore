package com.factstore.application

import com.factstore.exception.BadRequestException
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException

object PolicyYamlValidator {

    fun validate(yaml: String) {
        val parsed = try {
            Yaml().load<Any>(yaml)
        } catch (e: YAMLException) {
            throw BadRequestException("policyYaml is not valid YAML")
        }

        if (parsed !is Map<*, *>) {
            throw BadRequestException("policyYaml must contain a 'rules' list")
        }

        val rules = parsed["rules"]
        if (rules == null || rules !is List<*>) {
            throw BadRequestException("policyYaml must contain a 'rules' list")
        }
    }
}
