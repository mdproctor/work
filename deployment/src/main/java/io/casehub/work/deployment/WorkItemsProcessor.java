package io.casehub.work.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;

/**
 * Quarkus build-time processor for the WorkItems extension.
 * Registers the "workitems" feature and SQL migration resources for native image.
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
}
