plugins {
    alias(libs.plugins.versions)
    id("hu.bme.mit.gamma.gradle.eclipse")
}

tasks.wrapper {
    version = "8.3"
    distributionType = Wrapper.DistributionType.ALL
}
