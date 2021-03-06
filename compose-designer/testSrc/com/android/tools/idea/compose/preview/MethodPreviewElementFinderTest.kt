/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import org.intellij.lang.annotations.Language
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Method that returns the text representation of the preview body for the given [PreviewElement]
 * and removes new lines and duplicate spaces.
 */
private fun PreviewElement.previewBodyAsCompactText(): String? =
  this.previewBodyPsi?.element?.text?.replace("\\s+".toRegex(), " ")

class MethodPreviewElementFinderTest : ComposeLightJavaCodeInsightFixtureTestCase() {
  fun testFindPreviewMethods() {
    @Language("kotlin")
    val composeTest = myFixture.addFileToProject("src/Test.kt", """
      import androidx.compose.Composable
      import com.android.tools.preview.Configuration
      import com.android.tools.preview.Preview

      @Composable
      fun Preview1() {
        Preview() {
          Button("preview1") {
          }
        }
      }

      @Composable
      fun Preview2() {
        Preview(name = "preview2", configuration = Configuration(apiLevel = 12)) {
          Button("preview2") {
          }
        }
      }

      @Composable
      fun Preview3() {
        Preview(name = "preview3", configuration = Configuration(width = 1, height = 2, fontScale = .2f)) {
          Button("preview3") {
          }
        }
      }

      // This preview element will be found but the ComposeViewAdapter won't be able to render it
      @Composable
      fun PreviewWithParametrs(i: Int) {
        Preview(name = "Preview with parameters") {
          Button("preview3") {
          }
        }
      }

      @Composable
      fun NoPreviewComposable() {

      }

      fun PreviewMethodNotComposable() {
        Preview(name = "preview3", configuration = Configuration(width = 1, height = 2)) {
          Button {
          }
        }
      }
    """.trimIndent()).toUElement() as UFile

    val elements = MethodPreviewElementFinder.findPreviewMethods(composeTest)
    assertEquals(4, elements.size)
    elements[1].let {
      assertEquals("preview2", it.displayName)
      assertEquals(12, it.configuration.apiLevel)
      assertNull(it.configuration.theme)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)
      assertEquals(1f, it.configuration.fontScale)

      assertEquals("{ Button(\"preview2\") { } }", it.previewBodyAsCompactText())
    }

    elements[2].let {
      assertEquals("preview3", it.displayName)
      assertEquals(1, it.configuration.width)
      assertEquals(2, it.configuration.height)
      assertEquals(.2f, it.configuration.fontScale)

      assertEquals("{ Button(\"preview3\") { } }", it.previewBodyAsCompactText())
    }

    elements[0].let {
      assertEquals(UNDEFINED_API_LEVEL, it.configuration.apiLevel)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.width)
      assertEquals(UNDEFINED_DIMENSION, it.configuration.height)

      assertEquals("{ Button(\"preview1\") { } }", it.previewBodyAsCompactText())
    }

    elements[3].let {
      assertEquals("Preview with parameters", it.displayName)
    }
  }

  fun testNoDuplicatePreviewElements() {
    @Language("kotlin")
    val composeTest = myFixture.addFileToProject("src/Test.kt", """
      import com.android.tools.preview.Preview
      import com.android.tools.preview.Configuration
      import androidx.compose.Composable

      @Composable
      fun Preview1() {
        Preview() {
        }
      }

      @Composable
      fun Preview1() {
        Preview(name = "preview2", configuration = Configuration(apiLevel = 12)) {
        }
      }
    """.trimIndent()).toUElement() as UFile

    val elements = MethodPreviewElementFinder.findPreviewMethods(composeTest)
    assertEquals(1, elements.size)
    // Check that we keep the first element
    assertEmpty(elements[0].displayName)
  }

  fun testFindPreviewPackage() {
    @Language("kotlin")
    val notPreviewAnnotation = myFixture.addFileToProject("src/com/android/notpreview/Preview.kt", """
      package com.android.notpreview

      fun Preview(name: String? = null,
                  configuration: Configuration? = null,
                  children: () -> Unit) {
          children()
      }
    """.trimIndent())

    @Language("kotlin")
    val composeTest = myFixture.addFileToProject("src/Test.kt", """
      import com.android.notpreview.Preview
      import androidx.compose.Composable

      @Composable
      fun Row(children: () -> Unit) {

      }

      @Composable
      fun Button() {
      }

      // Test comment
      @Composable
      fun PreviewMethod() {
        Preview(name = "preview3", configuration = Configuration(width = 1, height = 2)) {
          Row {
            Button {
            }
          }
        }
      }
    """.trimIndent())

    assertEquals(0, MethodPreviewElementFinder.findPreviewMethods(composeTest.toUElement() as UFile).size)
  }
}