/*
 * Copyright (C) 2015 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.artifactory.descriptor.repo.RepoLayout
import org.artifactory.resource.ResourceStreamHandle
import org.artifactory.util.AlreadyExistsException

def propList = ['name': [
        CharSequence.class, 'string',
        { c, v -> c.name = v ?: null }
    ], 'artifactPathPattern': [
        CharSequence.class, 'string',
        { c, v -> c.artifactPathPattern = v ?: null }
    ], 'distinctiveDescriptorPathPattern': [
        Boolean.class, 'boolean',
        { c, v -> c.distinctiveDescriptorPathPattern = v ?: false }
    ], 'descriptorPathPattern': [
        CharSequence.class, 'string',
        { c, v -> c.descriptorPathPattern = v ?: null }
    ], 'folderIntegrationRevisionRegExp': [
        CharSequence.class, 'string',
        { c, v -> c.folderIntegrationRevisionRegExp = v ?: null }
    ], 'fileIntegrationRevisionRegExp': [
        CharSequence.class, 'string',
        { c, v -> c.fileIntegrationRevisionRegExp = v ?: null }]]

executions {
    getLayoutsList(httpMethod: 'GET') { params ->
        def cfg = ctx.centralConfig.descriptor.repoLayouts
        if (cfg == null) cfg = []
        def json = cfg.collect { it.name }
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    getLayout(httpMethod: 'GET') { params ->
        def name = params?.get('name')?.get(0) as String
        if (!name) {
            message = 'A layout name is required'
            status = 400
            return
        }
        def layout = ctx.centralConfig.descriptor.getRepoLayout(name)
        if (layout == null) {
            message = "Layout with name '$name' does not exist"
            status = 404
            return
        }
        def json = [
            name: layout.name ?: null,
            artifactPathPattern: layout.artifactPathPattern ?: null,
            distinctiveDescriptorPathPattern:
            layout.isDistinctiveDescriptorPathPattern() ?: false,
            descriptorPathPattern: layout.descriptorPathPattern ?: null,
            folderIntegrationRevisionRegExp:
            layout.folderIntegrationRevisionRegExp ?: null,
            fileIntegrationRevisionRegExp:
            layout.fileIntegrationRevisionRegExp ?: null]
        message = new JsonBuilder(json).toPrettyString()
        status = 200
    }

    deleteLayout(httpMethod: 'DELETE') { params ->
        def name = params?.get('name')?.get(0) as String
        if (!name) {
            message = 'A layout name is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def layout = cfg.removeRepoLayout(name)
        if (layout == null) {
            message = "Layout with name '$name' does not exist"
            status = 404
            return
        }
        ctx.centralConfig.descriptor = cfg
        status = 200
    }

    addLayout() { params, ResourceStreamHandle body ->
        def reader = new InputStreamReader(body.inputStream, 'UTF-8')
        def json = null
        try {
            json = new JsonSlurper().parse(reader)
        } catch (groovy.json.JsonException ex) {
            message = "Problem parsing JSON: $ex.message"
            status = 400
            return
        }
        if (!(json instanceof Map)) {
            message = 'Provided value must be a JSON object'
            status = 400
            return
        }
        if (!json['name']) {
            message = 'A layout name is required'
            status = 400
            return
        }
        def err = null
        def layout = new RepoLayout()
        propList.each { k, v ->
            if (!err && json[k] != null && !(v[0].isInstance(json[k]))) {
                err = "Property '$k' is type"
                err += " '${json[k].getClass().name}',"
                err += " should be a ${v[1]}"
            } else v[2](layout, json[k])
        }
        if (err) {
            message = err
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        try {
            cfg.addRepoLayout(layout)
        } catch (AlreadyExistsException ex) {
            message = "Layout with name '${json['name']}' already exists"
            status = 409
            return
        }
        ctx.centralConfig.descriptor = cfg
        status = 200
    }

    updateLayout() { params, ResourceStreamHandle body ->
        def name = params?.get('name')?.get(0) as String
        if (!name) {
            message = 'A layout name is required'
            status = 400
            return
        }
        def cfg = ctx.centralConfig.mutableDescriptor
        def layout = cfg.getRepoLayout(name)
        if (layout == null) {
            message = "Layout with name '$name' does not exist"
            status = 404
            return
        }
        def reader = new InputStreamReader(body.inputStream, 'UTF-8')
        def json = null
        try {
            json = new JsonSlurper().parse(reader)
        } catch (groovy.json.JsonException ex) {
            message = "Problem parsing JSON: $ex.message"
            status = 400
            return
        }
        if (!(json instanceof Map)) {
            message = 'Provided JSON value must be a JSON object'
            status = 400
            return
        }
        if ('name' in json.keySet()) {
            if (!json['name']) {
                message = 'A layout name must not be empty'
                status = 400
                return
            } else if (json['name'] != name
                       && cfg.isRepoLayoutExists(json['name'])) {
                message = "Layout with name '${json['name']}' already exists"
                status = 409
                return
            }
        }
        def err = null
        propList.each { k, v ->
            if (!err && k in json.keySet()) {
                if (json[k] && !(v[0].isInstance(json[k]))) {
                    err = "Property '$k' is type"
                    err += " '${json[k].getClass().name}',"
                    err += " should be a ${v[1]}"
                } else v[2](layout, json[k])
            }
        }
        if (err) {
            message = err
            status = 400
            return
        }
        ctx.centralConfig.descriptor = cfg
        status = 200
    }
}