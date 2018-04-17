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

import com.android.annotations.NonNull
import com.android.annotations.Nullable
import com.android.build.gradle.internal.transforms.DexTransform
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.DefaultDexOptions
import com.android.builder.core.DexByteCodeConverter
import com.android.builder.core.DexOptions
import com.android.builder.core.ErrorReporter
import com.android.builder.sdk.TargetInfo
import com.android.ide.common.process.JavaProcessExecutor
import com.android.ide.common.process.ProcessException
import com.android.ide.common.process.ProcessExecutor
import com.android.ide.common.process.ProcessOutputHandler
import com.android.utils.ILogger
import groovy.transform.CompileStatic

import java.lang.reflect.Field

/**
 * proxy the androidBuilder that plugin 1.5.0 to add '--minimal-main-dex' options.
 *
 * @author ceabie
 */
public class InjectConverter extends DexByteCodeConverter {

    Collection<String> mAddParams;
    DexByteCodeConverter mCodeConverter;
    File mMainDexListFileDK;

    InjectConverter(ILogger logger, TargetInfo targetInfo,
            JavaProcessExecutor javaProcessExecutor, boolean verboseExec,
            ErrorReporter errorReporter) {
        super(logger, targetInfo, javaProcessExecutor, verboseExec, errorReporter)
    }

    public void convertByteCode(Collection<File> inputs, File outDexFolder, boolean multidex, File mainDexList,
            DexOptions dexOptions, ProcessOutputHandler processOutputHandler, int minSdkVersion)
            throws IOException, InterruptedException, ProcessException {

        if (mainDexList == null) {
            mainDexList = mMainDexListFileDK
        }
        DefaultDexOptions newdexoptions = dexOptions
        if (mAddParams != null) {
            newdexoptions = DefaultDexOptions.copyOf(dexOptions)
            List<String> params = dexOptions.additionalParameters
            if (params == null) {
                params = new ArrayList<>()
            }
            mergeParams(params, mAddParams)
            newdexoptions.setAdditionalParameters(params)
            println "----------------------------"
        }
        mCodeConverter.convertByteCode(inputs, outDexFolder, multidex, mainDexList, newdexoptions,
                processOutputHandler, 16)
    }

    public static void proxyConverter(DexTransform transform, Collection<String> addParams, File mainDexList) {
        if (addParams != null && addParams.size() > 0) {

            accessibleField(DexTransform.class, "dexByteCodeConverter").set(transform, getProxyDexByteCodeConverter(transform.dexByteCodeConverter, addParams, mainDexList))
        }
    }

    @CompileStatic
    static void mergeParams(List<String> additionalParameters, Collection<String> addParams) {
        List<String> mergeParam = new ArrayList<>()
        for (String param : addParams) {
            if (!additionalParameters.contains(param)) {
                mergeParam.add(param)
            }
        }

        if (mergeParam.size() > 0) {
            additionalParameters.addAll(mergeParam)
        }
    }

    private static DexByteCodeConverter getProxyDexByteCodeConverter(DexByteCodeConverter orgConverter,
            Collection<String> addParams,
            File mainDexList) {
        InjectConverter myconverter = new InjectConverter(orgConverter.mLogger,
                orgConverter.mTargetInfo,
                orgConverter.mJavaProcessExecutor,
                orgConverter.mVerboseExec,
                orgConverter.errorReporter)
        myconverter.mAddParams = addParams
        myconverter.mCodeConverter = orgConverter
        myconverter.mMainDexListFileDK = mainDexList
        return myconverter
    }

    @CompileStatic
    private static Field accessibleField(Class cls, String field) {
        Field f = cls.getDeclaredField(field)
        f.setAccessible(true)
        return f
    }
}
