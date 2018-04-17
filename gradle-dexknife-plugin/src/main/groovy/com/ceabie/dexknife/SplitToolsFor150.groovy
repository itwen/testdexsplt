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
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.transforms.DexTransform
import com.android.sdklib.AndroidVersion
import org.gradle.api.Project
import org.gradle.api.file.FileCollection

/**
 * the spilt tools for plugin 1.5.0.
 *
 * @author ceabie
 */
public class SplitToolsFor150 extends DexSplitTools {

    public static final String PRINT_TAG = "------[lingyi tag]-----: "
    public static boolean isCompat() {
//         if (getAndroidPluginVersion() < 200) {
//             return true;
//         }

        return true;
    }

    public static void processSplitDex(Project project, ApplicationVariant variant) {
        VariantScope variantScope = variant.getVariantData().getScope()

        if (isInInstantRunMode(variantScope)) {
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
        TransformTask multidexTask

        String name = variant.name.capitalize()
        boolean minifyEnabled = variant.buildType.minifyEnabled

        // find the task we want to process
        project.tasks.matching {
            ((it instanceof TransformTask) && it.name.endsWith(name)) // TransformTask
        }.each { TransformTask theTask ->
            Transform transform = theTask.transform
            String transformName = transform.name

            printLog("transformName:"+transformName)
//            if (minifyEnabled && "proguard".equals(transformName)) { // ProGuardTransform
//                proGuardTask = theTask
//            } else
            if ("jarMerging".equalsIgnoreCase(transformName)) {
                jarMergingTask = theTask
                printLog("transformName  jarMerging:"+transformName)
            } else if ("dex".equalsIgnoreCase(transformName)) { // DexTransform
                dexTask = theTask
                printLog("transformName  dex:"+transformName)
            }else if ("multidexlist".equalsIgnoreCase(transformName)) { // DexTransform
                multidexTask = theTask
                printLog("transformName  dex:"+transformName)
            }
        }

        if(multidexTask != null){
            multidexTask.doFirst {

            }
        }

        if (dexTask != null) {

            dexTask.inputs.file DEX_KNIFE_CFG_TXT

            dexTask.doFirst {
                startDexKnife()

                File mergedJar = null
                File mappingFile = variant.mappingFile
                DexTransform dexTransform = it.transform
                FileCollection adtMainDexList = dexTransform.mainDexListFile

                printLog("dexTransform.mainDexListFile:"+adtMainDexList.getFiles().toString())

                DexKnifeConfig dexKnifeConfig = getDexKnifeConfig(project)

                BufferedReader reader = new BufferedReader(new FileReader(adtMainDexList.getSingleFile()))

                def line = null;
                while ((line = reader.readLine()) != null){
                    printLog(line)
                }

                // 非混淆的，从合并后的jar文件中提起mainlist；
                // 混淆的，直接从mapping文件中提取
                if (minifyEnabled) {
                    printLog("minifyEnabled: true")
                } else {
                    if (jarMergingTask != null) {
                        Transform transform = jarMergingTask.transform
                        def outputProvider = jarMergingTask.outputStream.asOutput()
                        mergedJar = outputProvider.getContentLocation("combined",
                                transform.getOutputTypes(),
                                transform.getScopes(), Format.JAR)
                        printLog("jarMergingTask: false")
                    }

                }

                if (processMainDexList(project, minifyEnabled, mappingFile, mergedJar,
                        adtMainDexList.getSingleFile(), dexKnifeConfig)) {

                    // replace android gradle plugin's maindexlist.txt
                    if (adtMainDexList.getSingleFile() != null) {
                        adtMainDexList.getSingleFile().delete()
                        project.copy {
                            from MAINDEXLIST_TXT
                            into adtMainDexList.getSingleFile().parentFile
                        }
                    } else {
                        adtMainDexList = project.file(MAINDEXLIST_TXT)
                    }

                      InjectConverter.proxyConverter(dexTransform,dexKnifeConfig.additionalParameters,adtMainDexList.getSingleFile())
                }

                endDexKnife()
            }
        } else {
            System.err.println("process task error")
        }
    }

    private static boolean isInInstantRunMode(VariantScope scope) {
        try {
            def instantRunBuildContext = scope.getInstantRunBuildContext()
            return instantRunBuildContext.isInInstantRunMode()
        } catch (Throwable e) {
        }
        return false
    }

    private static boolean isInTestingMode(ApplicationVariant variant) {
        return (variant.getVariantData().getType().isForTesting());
    }

    private static int getMinSdk(VariantScope variantScope) {
        def version = variantScope.getMinSdkVersion()
        return version != null? version.getApiLevel(): 0;
    }

    private static int getTargetSdk(VariantScope variantScope) {
        def version = variantScope.getVariantConfiguration().getTargetSdkVersion()
        return version != null? version.getApiLevel(): 0;
    }

    private static boolean isLegacyMultiDexMode(VariantScope variantScope) {
        def configuration = variantScope.getVariantData().getVariantConfiguration()
        return configuration.isLegacyMultiDexMode()
    }

    private static void logProjectSetting(Project project, ApplicationVariant variant, String pluginVersion) {
        System.err.println("Please feedback below Log to  https://github.com/ceabie/DexKnifePlugin/issues")
        System.err.println("Feedback Log Start >>>>>>>>>>>>>>>>>>>>>>>")
        def variantScope = variant.getVariantData().getScope()
        GradleVariantConfiguration config = variantScope.getVariantConfiguration();

        println("AndroidPluginVersion: " + pluginVersion)
        println("variant: " + variant.name.capitalize())
        println("minifyEnabled: " + variant.buildType.minifyEnabled)
        println("FeatureLevel:  " + AndroidGradleOptions.getTargetFeatureLevel(project))
        println("MinSdkVersion: " + getMinSdk(variantScope))
        println("TargetSdkVersion: " + getTargetSdk(variantScope))
        println("isLegacyMultiDexMode: " + isLegacyMultiDexMode(variantScope))

        println("isInstantRunSupported: " + config.isInstantRunSupported())
        println("targetDeviceSupportsInstantRun: " + targetDeviceSupportsInstantRun(config, project))
        println("getPatchingPolicy: " + variantScope.getInstantRunBuildContext().getPatchingPolicy())
        System.err.println("Feedback Log End <<<<<<<<<<<<<<<<<<<<<<<<<<")
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

    private static void printLog(String log){
        println PRINT_TAG+log
    }
}