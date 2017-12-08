/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.newpsd

import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import org.fest.swing.core.ComponentFinder
import org.fest.swing.core.GenericTypeMatcher
import java.awt.Container
import javax.swing.JComponent

typealias TestBlock<T> = (T.() -> Unit)?

interface IdeFrameContainerFixture {
  val ideFrameFixture: IdeFrameFixture
  val container: Container
}

fun <T> TestBlock<T>.runTestOn(target: T) = this?.let { target.this() }

fun <T> IdeFrameFixture.openFromMenu(finder: (IdeFrameFixture) -> T, vararg path: String, block: TestBlock<T>) =
    block.runTestOn(openFromMenu(finder, path))

inline fun <reified T : JComponent> matcher(crossinline predicate: (T) -> Boolean): GenericTypeMatcher<T> =
    object : GenericTypeMatcher<T>(T::class.java) {
      override fun isMatching(component: T): Boolean = predicate(component)
    }

fun IdeFrameContainerFixture.robot() = ideFrameFixture.robot()
fun IdeFrameContainerFixture.finder() = ideFrameFixture.robot().finder()
inline fun <reified T : JComponent> ComponentFinder.findByType(root: Container) = findByType(root, T::class.java, true)
inline fun <reified T : JComponent> ComponentFinder.findByLabel(root: Container, label: String)
    = findByLabel(root, label, T::class.java, true)