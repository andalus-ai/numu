package tech.kayys.numou.project.spi.port;

import tech.kayys.numou.project.domain.project.ProjectNumber;

/**
 * Allocates the next human-readable project number.
 *
 * Numbering rules (per tenant, per year, per project type, ...) are a
 * platform concern, so the domain only sees the resulting
 * {@link ProjectNumber}.
 */
public interface ProjectNumberGenerator {

    ProjectNumber next();
}
