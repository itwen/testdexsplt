/*
 * Copyright (C) 2016 ceabie (https://github.com/ceabie/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ceabie.dexknife

import com.android.build.api.transform.Format
import com.android.build.api.transform.Transform
import com.android.build.gradle.AndroidGradleOptions
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.internal.core.GradleVariantConfiguration
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.transforms.DexTransform
import com.android.builder.model.OptionalCompilationStep
import com.android.sdklib.AndroidVersion
import org.gradle.api.Project

/**
 * the spilt tools for plugin 1.5.0.
 *
 * @author ceabie
 */
public class SplitToolsFor150 extends DexSplitTools {

    public static boolean isCompat() {
//         if (getAndroidPluginVersion() < 200) {
//             return true;
//         }

        return true;
    }

    public static void processSplitDex(Project project, ApplicationVariant variant) {
        if (isInInstantRunMode(variant)) {
            System.err.println("DexKnife: Instant Run mode, DexKnife is auto disabled!")
            return
        }

        if (isInTestingMode(variant)) {
            System.err.println("DexKnife: Testing mode, DexKnife is auto disabled!")
            return
        }

        TransformTask dexTask
//        TransformTask proGuardTask
        TransformTask jarMergingTask

        String name = variant.name.capitalize()
        boolean minifyEnabled = variant.buildType.minifyEnabled

        // find the task we want to process
        project.tasks.matching {
            ((it instanceof TransformTask) && it.name.endsWith(name)) // TransformTask
        }.each { TransformTask theTask ->
            Transform transform = theTask.transform
            String transformName = transform.name

//            if (minifyEnabled && "proguard".equals(transformName)) { // ProGuardTransform
//                proGuardTask = theTask
//            } else
            if ("jarMerging".equalsIgnoreCase(transformName)) {
                jarMergingTask = theTask
            } else if ("dex".equalsIgnoreCase(transformName)) { // DexTransform
                dexTask = theTask
            }
        }

        if (dexTask != null && ((DexTransform) dexTask.transform).multiDex) {
            dexTask.inputs.file DEX_KNIFE_CFG_TXT

            dexTask.doFirst {
                startDexKnife()

                File mergedJar = null
                File mappingFile = variant.mappingFile
                DexTransform dexTransform = it.transform
                File adtMainDexList = dexTransform.mainDexListFile

                println("DexKnife Adt Main: " + adtMainDexList)

                if (adtMainDexList == null) {
                    System.err.println("DexKnife: Not LegacyMultiDexMode, suggest-keep and suggest-split will merge into global filter.")
                    if (!minifyEnabled) {
                        System.err.println("DexKnife: Not LegacyMultiDexMode and Not minifyEnabled, DexKnife is auto disabled!")
                        logProjectSetting(project, variant)
                        return
                    }
                }


                DexKnifeConfig dexKnifeConfig = getDexKnifeConfig(project, adtMainDexList)

                // 非混淆的，从合并后的jar文件中提起mainlist；
                // 混淆的，直接从mapping文件中提取
                if (minifyEnabled) {
                    println("DexKnife-From Mapping: " + mappingFile)
                } else {
                    if (jarMergingTask != null) {
                        Transform transform = jarMergingTask.transform
                        def outputProvider = jarMergingTask.outputStream.asOutput()
                        mergedJar = outputProvider.getContentLocation("combined",
                                transform.getOutputTypes(),
                                transform.getScopes(), Format.JAR)
                    }

                    println("DexKnife-From MergedJar: " + mergedJar)
                }

                if (processMainDexList(project, minifyEnabled, mappingFile, mergedJar,
                        adtMainDexList, dexKnifeConfig)) {

                    int version = getAndroidPluginVersion(getAndroidGradlePluginVersion())
                    println("DexKnife: AndroidPluginVersion: " + version)

                    // replace android gradle plugin's maindexlist.txt
                    if (adtMainDexList != null) {
                        adtMainDexList.delete()
                        project.copy {
                            from MAINDEXLIST_TXT
                            into adtMainDexList.parentFile
                        }
                    } else {
                        adtMainDexList = project.file(MAINDEXLIST_TXT)
                    }

                    // after 2.2.0, it can additionalParameters, but it is a copy in task

                    // 替换 AndroidBuilder
                    InjectAndroidBuilder.proxyAndroidBuilder(dexTransform,
                            dexKnifeConfig.additionalParameters,
                            adtMainDexList)

                }

                endDexKnife()
            }
        }
    }

    private static boolean isInInstantRunMode(ApplicationVariant variant) {
        try {
            def scope = variant.getVariantData().getScope()
            def instantRunBuildContext = scope.getInstantRunBuildContext()
            return instantRunBuildContext.isInInstantRunMode()
        } catch (Throwable e) {
        }
        return false
    }

    private static boolean isInTestingMode(ApplicationVariant variant) {
        return (variant.getVariantData().getType().isForTesting());
    }

    private static void logProjectSetting(Project project, ApplicationVariant variant) {
        System.err.println("Please upload below Log to  https://github.com/ceabie/DexKnifePlugin/issues")
        System.err.println("Upload Log Start >>>>>>>>>>>>>>>>>>>>>>>")
        println("variant: " + variant.name.capitalize())
        println("FeatureLevel: " + AndroidGradleOptions.getTargetFeatureLevel(project))

        def variantScope = variant.getVariantData().getScope()
        def configuration = variantScope.getVariantData().getVariantConfiguration()
        GradleVariantConfiguration config = variantScope.getVariantConfiguration();
        def optionalCompilationSteps = AndroidGradleOptions.getOptionalCompilationSteps(project);
        def context = variantScope.getInstantRunBuildContext()

        println("isLegacyMultiDexMode: " + configuration.isLegacyMultiDexMode())
        println("isInstantRunSupported: " + config.isInstantRunSupported())
        println("targetDeviceSupportsInstantRun: " + targetDeviceSupportsInstantRun(config, project))
        println("INSTANT_DEV: " + optionalCompilationSteps.contains(OptionalCompilationStep.INSTANT_DEV))
        println("getPatchingPolicy: " + context.getPatchingPolicy())
        System.err.println("Upload Log End <<<<<<<<<<<<<<<<<<<<<<<")
    }

    private static boolean targetDeviceSupportsInstantRun(
            GradleVariantConfiguration config,
            Project project) {
        if (config.isLegacyMultiDexMode()) {
            // We don't support legacy multi-dex on Dalvik.
            return AndroidGradleOptions.getTargetFeatureLevel(project) >=
                    AndroidVersion.ART_RUNTIME.getFeatureLevel();
        }

        return true;
    }
}