#!/usr/bin/env groovy

/**
 * Created by Maciej Gasiorowski on 10/07/2017.
 *
 * Run android unit tests
 *
 * nodeLabel - label where unit tests will be run
 * junitTestReportFile - optional file name with unit tests reports
 * gradleTasksDebug - gradle tasks to build binary for debug
 * gradleTasksRelease - gradle tasks to build binary for release
 * useWiremock - optional argument to use wiremock (default false)
 * wiremockVersion - optional argument to set wiremock version to use (default is used version on nodes)
 * wiremockPort - optional argument to set wiremock port to use (default 8080)
 *
 */

import io.jenkins.mobilePipeline.AndroidUtilities
import io.jenkins.mobilePipeline.Utilities
import io.jenkins.mobilePipeline.ReactNativeUtilities

def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    timeout(60) {
        node("${config.nodeLabel}") {
            def androidUtils = new AndroidUtilities(steps)
            def utils = new Utilities(steps)
            def reactNativeUtils = new ReactNativeUtilities(steps)
            def gradleTasks = androidUtils.getGradleTasks(env.BUILD_TYPE, config.gradleTasksRelease, config.gradleTasksDebug)
            def defaultGradleOptions = androidUtils.setDefaultGradleOptions()
            def junitTestReportFile = androidUtils.getJunitTestReportFile(config.junitTestReportFile)
            def buildWorkspace = utils.getBuildWorkspace(config.isReactNative, "android", env.WORKSPACE)
            
            stage("${utils.getStageSuffix(config.stageSuffix)}Unit Tests") {
                deleteDir()
                unstash "workspace"
                reactNativeUtils.unstashNpmCache()
                utils.runWiremock(config.useWiremock, env.WORKSPACE, config.wiremockVersion, config.wiremockPort)
                try {
                    dir(buildWorkspace) {
                        withEnv(["GRADLE_USER_HOME=${env.WORKSPACE}/.gradle"]) {
                            androidUtils.unstashGradleCache()
                            androidUtils.setAndroidBuildCache(env.WORKSPACE)
                            sh "chmod +x gradlew"
                            sh """#!/bin/bash -xe 
                              ./gradlew ${defaultGradleOptions} -PversionCode=${env.BUILD_NUMBER} clean ${gradleTasks}
                           """
                        }
                    }
                } catch (exception) {
                    utils.handleException(exception)
                } finally {
                    junit allowEmptyResults: true, testResults: junitTestReportFile
                    androidUtils.stashGradleCache()
                    androidUtils.archieveGradleProfileReport("unit-tests")
                    utils.shutdownWiremock(config.useWiremock, config.wiremockPort)
                }
            }
        }
    }
}
