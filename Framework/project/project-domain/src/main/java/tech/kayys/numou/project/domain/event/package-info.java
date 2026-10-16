/**
 * Domain events published by the Project bounded context.
 *
 * Events are raised inside the aggregates and published by the
 * application layer through the foundation {@code EventPublisher}
 * outbound port - aggregates never talk to Kafka or the database.
 */
package tech.kayys.syirkah.project.domain.event;
