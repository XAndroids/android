/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.experimental.codeanalysis.datastructs.value.impl;

import com.android.tools.idea.experimental.codeanalysis.datastructs.value.Param;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * Created by haowei on 6/14/16.
 */
public class ParamImpl implements Param {

  private PsiParameter mPsiRef;

  @Override
  public PsiType getType() {
    return mPsiRef.getType();
  }

  @Override
  public PsiElement getPsiRef() {
    return mPsiRef;
  }

  @Override
  public String getSimpleName() {
    if (mPsiRef != null) {
      return mPsiRef.getName();
    }
    else {
      return "[Null Param]";
    }
  }

  public ParamImpl(@NotNull PsiParameter psiRef) {
    this.mPsiRef = psiRef;
  }
}
