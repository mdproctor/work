package io.casehub.work.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;

/**
 * Quarkus build-time processor for the WorkItems extension.
 * Registers the "workitems" feature, SQL migration resources for native image,
 * and WorkItemsMigrationCustomizer as an unremovable CDI bean.
 */
class WorkItemsProcessor {

    private static final String FEATURE = "workitems";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    NativeImageResourcePatternsBuildItem registerMigrationResources() {
        return NativeImageResourcePatternsBuildItem.builder()
                .includeGlob("db/work/migration/*.sql")
                .build();
    }

    @BuildStep
    AdditionalBeanBuildItem registerMigrationCustomizer() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses("io.casehub.work.runtime.flyway.WorkItemsMigrationCustomizer")
                .setUnremovable()
                .build();
    }
}
