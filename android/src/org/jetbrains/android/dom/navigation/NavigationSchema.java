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
package org.jetbrains.android.dom.navigation;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.google.common.collect.*;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.android.ClassMaps;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.android.SdkConstants.TAG_DEEPLINK;
import static org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType.*;

/**
 * Provides information on OOTB and user-specified navigation tags and attributes.
 *
 * TODO: support updates (e.g. custom navigator created)
 */
public class NavigationSchema implements Disposable {
  public static final String TAG_ACTION = "action";
  public static final String TAG_ARGUMENT = "argument";
  public static final String ATTR_DESTINATION = "destination";

  // TODO: move these to a UI-specific place
  public static final String INCLUDE_GRAPH_LABEL = "Include Graph";
  public static final String ACTION_LABEL = "Action";

  public static final String TAG_INCLUDE = "include";

  private static final String NAVIGATOR_CLASS_NAME = "android.arch.navigation.Navigator";
  private static final String ACTION_CLASS_NAME = "android.arch.navigation.NavAction";

  // TODO: it would be nice if this mapping were somehow supplied by the platform
  private static final Map<String, DestinationType> NAV_CLASS_TO_TYPE = ImmutableMap.of(
    "android.arch.navigation.NavGraph", DestinationType.NAVIGATION,
    "android.arch.navigation.FragmentNavigator.Destination", DestinationType.FRAGMENT,
    "android.arch.navigation.ActivityNavigator.Destination", DestinationType.ACTIVITY);

  private static final String ANNOTATION_NAV_TAG_NAME = "android.arch.navigation.Navigator.Name";

  public static final String ATTR_NAV_TYPE = "navType";
  public static final String ATTR_START_DESTINATION = "startDestination";
  public static final String ATTR_GRAPH = "graph";
  public static final String ATTR_POP_UP_TO = "popUpTo";
  public static final String ATTR_POP_UP_TO_INCLUSIVE = "popUpToInclusive";
  public static final String ATTR_SINGLE_TOP = "launchSingleTop";
  public static final String ATTR_DOCUMENT = "launchDocument";
  public static final String ATTR_CLEAR_TASK = "clearTask";
  public static final String ATTR_ENTER_ANIM = "enterAnim";
  public static final String ATTR_EXIT_ANIM = "exitAnim";
  public static final String ATTR_ACTION = "action";
  public static final String ATTR_DATA = "data";
  public static final String ATTR_DATA_PATTERN = "dataPattern";

  // TODO: it would be nice if this mapping were somehow supplied by the platform
  public static final Map<String, DestinationType> DESTINATION_SUPERCLASS_TO_TYPE = ImmutableMap.of(
    SdkConstants.CLASS_ACTIVITY, DestinationType.ACTIVITY,
    SdkConstants.CLASS_FRAGMENT, DestinationType.FRAGMENT,
    SdkConstants.CLASS_V4_FRAGMENT, DestinationType.FRAGMENT);

  private static final Map<AndroidFacet, NavigationSchema> ourSchemas = new HashMap<>();

  // TODO: it would be nice to have this generated dynamically, but there's no way to know what the default navigator for a type should be.
  private Map<DestinationType, String> myTypeToRootTag = ImmutableMap.of(
    DestinationType.FRAGMENT, "fragment",
    DestinationType.ACTIVITY, "activity",
    DestinationType.NAVIGATION, "navigation"
  );

  private Map<String, DestinationType> myTagToDestinationType;
  private Map<PsiClass, String> myNavigatorClassToTag;

  private final ClassMaps myClassMaps;
  private PsiClass myActionClass;
  public static final String ATTR_DEFAULT_VALUE = "defaultValue";

  public enum DestinationType {
    NAVIGATION,
    FRAGMENT,
    ACTIVITY,
    OTHER
  }

  @VisibleForTesting
  public static final String ENABLE_NAV_PROPERTY = "enable.nav.editor";

  public static boolean enableNavigationEditor() {
    return Boolean.getBoolean(ENABLE_NAV_PROPERTY);
  }

  @NotNull
  public static synchronized NavigationSchema getOrCreateSchema(@NotNull AndroidFacet facet) {
    NavigationSchema result = ourSchemas.get(facet);
    if (result == null) {
      result = new NavigationSchema(facet);
      Disposer.register(facet, result);
      result.init();
      ourSchemas.put(facet, result);
    }
    return result;
  }

  private NavigationSchema(@NotNull AndroidFacet facet) {
    myClassMaps = ClassMaps.getInstance(facet);
  }

  @Override
  public void dispose() {
    ourSchemas.remove(myClassMaps.getFacet());
  }

  @NotNull
  private static DestinationType getType(@NotNull PsiClass subNav, @NotNull PsiClass navigatorRoot, PsiTypeParameter destinationTypeParam) {
    PsiType resolved =
      TypeConversionUtil.getSuperClassSubstitutor(navigatorRoot, PsiTypesUtil.getClassType(subNav)).substitute(destinationTypeParam);

    if (resolved == null) {
      // Shouldn't happen unless there's code errors in the project
      return DestinationType.OTHER;
    }
    return NAV_CLASS_TO_TYPE.getOrDefault(resolved.getCanonicalText(), DestinationType.OTHER);
  }

  @Nullable
  private static String getTagAttributeValue(@NotNull PsiClass subNav) {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(subNav, ANNOTATION_NAV_TAG_NAME);
    return annotation == null ? null : AnnotationUtil.getStringAttributeValue(annotation, "value");
  }

  private void init() {
    Project project = myClassMaps.getFacet().getModule().getProject();
    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    PsiClass navigatorRoot = javaPsiFacade.findClass(NAVIGATOR_CLASS_NAME, GlobalSearchScope.allScope(project));
    if (navigatorRoot == null) {
      // TODO: handle this nicely
      Logger.getInstance(getClass()).warn("Navigator class not found.");
      return;
    }
    PsiTypeParameter destinationTypeParam = navigatorRoot.getTypeParameters()[0];

    Map<String, DestinationType> tagToType = new HashMap<>();
    Map<PsiClass, String> classToTag = new HashMap<>();

    for (PsiClass navClass : getClassMap(NAVIGATOR_CLASS_NAME).values()) {
      if (navClass.equals(navigatorRoot)) {
        // Don't keep the root navigator
        continue;
      }

      DestinationType type = getType(navClass, navigatorRoot, destinationTypeParam);
      String tag = getTagAttributeValue(navClass);

      if (tag == null) {
        continue;
      }

      if (tagToType.getOrDefault(tag, type) != type) {
        Logger.getInstance(NavigationSchema.class).warn("Multiple destination types for tag " + navClass);
      }
      tagToType.put(tag, type);
      classToTag.put(navClass, tag);
    }
    // Doesn't come from library, so have to add manually.
    tagToType.put(TAG_INCLUDE, DestinationType.NAVIGATION);

    myNavigatorClassToTag = classToTag;
    myTagToDestinationType = tagToType;

    myActionClass = javaPsiFacade.findClass(ACTION_CLASS_NAME, GlobalSearchScope.allScope(project));
  }


  // TODO: it seems like the framework should do this somehow
  @NotNull
  public Multimap<Class<? extends AndroidDomElement>, String> getDestinationSubtags(@NotNull String tagName) {
    if (tagName.equals(TAG_ACTION)) {
      return ImmutableSetMultimap.of(ArgumentElement.class, TAG_ARGUMENT);
    }
    DestinationType type = getDestinationType(tagName);
    if (type == null) {
      return ImmutableListMultimap.of();
    }
    Multimap<Class<? extends AndroidDomElement>, String> result = HashMultimap.create();
    if (!tagName.equals(TAG_INCLUDE)) {
      if (type == DestinationType.NAVIGATION) {
        myTagToDestinationType.keySet().forEach(subTag -> result.put(NavDestinationElement.class, subTag));
      }
      if (type != DestinationType.ACTIVITY) {
        result.put(NavActionElement.class, TAG_ACTION);
      }
      result.put(DeeplinkElement.class, TAG_DEEPLINK);
      result.put(ArgumentElement.class, TAG_ARGUMENT);
    }
    return result;
  }

  @Nullable
  public DestinationType getDestinationType(@NotNull String tag) {
    return myTagToDestinationType.get(tag);
  }

  @Nullable
  public String getDefaultTag(@NotNull DestinationType type) {
    return myTypeToRootTag.get(type);
  }

  @Nullable
  public String getTagForComponentSuperclass(@NotNull String superclassName) {
    DestinationType type = DESTINATION_SUPERCLASS_TO_TYPE.get(superclassName);
    if (type != null) {
      return getDefaultTag(type);
    }
    return null;
  }

  @Contract("null -> null")
  @Nullable
  public String findTagForComponent(@Nullable PsiClass layoutClass) {
    while (layoutClass != null) {
      String qName = layoutClass.getQualifiedName();
      if (qName != null) {
        String tag = getTagForComponentSuperclass(qName);
        if (tag != null) {
          return tag;
        }
      }
      layoutClass = layoutClass.getSuperClass();
    }
    return null;
  }

  @NotNull
  public List<String> getPossibleRoots() {
    return myTagToDestinationType.keySet().stream()
      .filter(tag -> myTagToDestinationType.get(tag) == DestinationType.NAVIGATION)
      .collect(Collectors.toList());
  }

  @NotNull
  public String getTag(@NotNull PsiClass navigatorClass) {
    return myNavigatorClassToTag.get(navigatorClass);
  }

  @Nullable
  public DestinationType getTypeForNavigatorClass(@NotNull PsiClass navigatorClass) {
    String tag = myNavigatorClassToTag.get(navigatorClass);
    return tag == null ? null : myTagToDestinationType.get(tag);
  }

  @NotNull
  public Set<PsiClass> getDestinationClassesByTagSlowly(@NotNull String tagName) {
    return myNavigatorClassToTag.keySet().stream().filter(c -> myNavigatorClassToTag.get(c).equals(tagName)).collect(Collectors.toSet());
  }

  public Map<String, DestinationType> getTagTypeMap() {
    return Collections.unmodifiableMap(myTagToDestinationType);
  }

  public Map<PsiClass, String> getNavigatorClassTagMap() {
    return Collections.unmodifiableMap(myNavigatorClassToTag);
  }

  public PsiClass getActionClass() {
    return myActionClass;
  }

  @NotNull
  private Map<String, PsiClass> getClassMap(@NotNull String className) {
    Map<String, PsiClass> result = myClassMaps.getClassMap(className);
    if (result.isEmpty()) {
      // TODO: handle the not-synced-yet case
      throw new RuntimeException(className + " not found");
    }
    return result;
  }

  @NotNull
  public String getTagLabel(@NotNull String tag) {
    return getTagLabel(tag, false);
  }

  @NotNull
  public String getTagLabel(@NotNull String tag, boolean isRoot) {
    String text = null;
    if (TAG_INCLUDE.equals(tag)) {
      text = INCLUDE_GRAPH_LABEL;
    }
    else if (TAG_ACTION.equals(tag)) {
      text = ACTION_LABEL;
    }
    else {
      NavigationSchema.DestinationType type = getDestinationType(tag);
      if (type == NAVIGATION) {
        text = isRoot ? "Root Graph" : "Nested Graph";
      }
      else if (type == FRAGMENT) {
        text = "Fragment";
      }
      else if (type == ACTIVITY) {
        text = "Activity";
      }

      if (type == OTHER) {
        text = tag;
      }
      else if (type != null && !tag.equals(getDefaultTag(type))) {
        // If it's a custom tag, show it
        text += " (" + tag + ")";
      }
    }

    assert text != null;
    return text;
  }
}
