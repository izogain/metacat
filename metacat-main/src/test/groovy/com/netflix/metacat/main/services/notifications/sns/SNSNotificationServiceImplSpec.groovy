/*
 *
 *  Copyright 2016 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.metacat.main.services.notifications.sns

import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sns.model.NotFoundException
import com.amazonaws.services.sns.model.PublishResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.TextNode
import com.google.common.collect.Lists
import com.netflix.metacat.common.MetacatRequestContext
import com.netflix.metacat.common.QualifiedName
import com.netflix.metacat.common.dto.PartitionDto
import com.netflix.metacat.common.dto.PartitionsSaveResponseDto
import com.netflix.metacat.common.dto.TableDto
import com.netflix.metacat.common.dto.notifications.sns.messages.*
import com.netflix.metacat.common.server.events.*
import com.netflix.metacat.common.server.monitoring.Metrics
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import spock.lang.Specification

import java.util.concurrent.TimeUnit

/**
 * Tests for the SNSNotificationServiceImpl.
 *
 * @author tgianos
 * @since 0.1.47
 */
class SNSNotificationServiceImplSpec extends Specification {
    def client = Mock(AmazonSNSClient)
    def qName = QualifiedName.fromString(
        UUID.randomUUID().toString()
            + "/"
            + UUID.randomUUID().toString()
            + "/"
            + UUID.randomUUID().toString()
            + "/"
            + UUID.randomUUID().toString()
            + "/"
            + UUID.randomUUID().toString()
    )
    def mapper = Mock(ObjectMapper)
    def partitionArn = UUID.randomUUID().toString()
    def tableArn = UUID.randomUUID().toString()
    def registry = Mock(Registry)
    def id = Mock(Id)
    def counter = Mock(Counter)
    def timer = Mock(Timer)

    def service = new SNSNotificationServiceImpl(
        this.client,
        this.tableArn,
        this.partitionArn,
        this.mapper,
        this.registry
    )

    def requestContext = new MetacatRequestContext(
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString()
    )

    def "Will Notify On Partition Creation"() {
        def partitions = Lists.newArrayList(new PartitionDto(), new PartitionDto(), new PartitionDto())

        def event = new MetacatSaveTablePartitionPostEvent(
            this.qName,
            this.requestContext,
            this,
            partitions,
            Mock(PartitionsSaveResponseDto)
        )

        when:
        this.service.notifyOfPartitionAddition(event)

        then:
        3 * this.mapper.writeValueAsString(_ as AddPartitionMessage) >> UUID.randomUUID().toString()
        1 * this.mapper.writeValueAsString(_ as UpdateTablePartitionsMessage) >> UUID.randomUUID().toString()
        3 * this.client.publish(this.partitionArn, _ as String) >> new PublishResult()
        1 * this.client.publish(this.tableArn, _ as String) >> new PublishResult()
        3 * this.registry.createId(Metrics.CounterSNSNotificationPartitionAdd.name()) >> this.id
        1 * this.registry.createId(Metrics.CounterSNSNotificationTablePartitionAdd.name()) >> this.id
        4 * this.id.withTags(Metrics.statusSuccessMap) >> this.id
        4 * this.registry.counter(this.id) >> this.counter
        4 * this.counter.increment()
        4 * this.registry.timer(
            Metrics.TimerNotificationsPublishDelay.name(),
            "type",
            _ as String
        ) >> this.timer
        4 * this.timer.record(_ as Long, _ as TimeUnit)
    }

    def "Will Notify On Partition Deletion"() {
        def partitions = Lists.newArrayList(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString()
        )

        def event = new MetacatDeleteTablePartitionPostEvent(
            this.qName,
            this.requestContext,
            this,
            partitions
        )

        when:
        this.service.notifyOfPartitionDeletion(event)

        then:
        5 * this.mapper.writeValueAsString(_ as DeletePartitionMessage) >> UUID.randomUUID().toString()
        1 * this.mapper.writeValueAsString(_ as UpdateTablePartitionsMessage) >> UUID.randomUUID().toString()
        5 * this.client.publish(this.partitionArn, _ as String) >> new PublishResult()
        1 * this.client.publish(this.tableArn, _ as String) >> new PublishResult()
        5 * this.registry.createId(Metrics.CounterSNSNotificationPartitionDelete.name()) >> this.id
        1 * this.registry.createId(Metrics.CounterSNSNotificationTablePartitionDelete.name()) >> this.id
        6 * this.id.withTags(Metrics.statusSuccessMap) >> this.id
        6 * this.registry.counter(this.id) >> this.counter
        6 * this.counter.increment()
        6 * this.registry.timer(
            Metrics.TimerNotificationsPublishDelay.name(),
            "type",
            _ as String
        ) >> this.timer
        6 * this.timer.record(_ as Long, _ as TimeUnit)
    }

    def "Will Notify On Table Creation"() {
        def event = new MetacatCreateTablePostEvent(
            this.qName,
            this.requestContext,
            this,
            new TableDto()
        )

        when:
        this.service.notifyOfTableCreation(event)

        then:
        1 * this.mapper.writeValueAsString(_ as CreateTableMessage) >> UUID.randomUUID().toString()
        1 * this.client.publish(this.tableArn, _ as String) >> new PublishResult()
        1 * this.registry.createId(Metrics.CounterSNSNotificationTableCreate.name()) >> this.id
        1 * this.id.withTags(Metrics.statusSuccessMap) >> this.id
        1 * this.registry.counter(this.id) >> this.counter
        1 * this.registry.timer(
            Metrics.TimerNotificationsPublishDelay.name(),
            "type",
            _ as String
        ) >> this.timer
        1 * this.timer.record(_ as Long, _ as TimeUnit)
    }

    def "Will Notify On Table Deletion"() {
        def event = new MetacatDeleteTablePostEvent(
            this.qName,
            this.requestContext,
            this,
            new TableDto()
        )

        when:
        this.service.notifyOfTableDeletion(event)

        then:
        1 * this.mapper.writeValueAsString(_ as DeleteTableMessage) >> UUID.randomUUID().toString()
        1 * this.client.publish(this.tableArn, _ as String) >> new PublishResult()
        1 * this.registry.createId(Metrics.CounterSNSNotificationTableDelete.name()) >> this.id
        1 * this.id.withTags(Metrics.statusSuccessMap) >> this.id
        1 * this.registry.counter(this.id) >> this.counter
        1 * this.registry.timer(
            Metrics.TimerNotificationsPublishDelay.name(),
            "type",
            _ as String
        ) >> this.timer
        1 * this.timer.record(_ as Long, _ as TimeUnit)
    }

    def "Will Notify On Table Rename"() {
        def event = new MetacatRenameTablePostEvent(
            this.qName,
            this.requestContext,
            this,
            new TableDto(),
            new TableDto()
        )

        when:
        this.service.notifyOfTableRename(event)

        then:
        2 * this.mapper.valueToTree(_ as TableDto) >> new TextNode(UUID.randomUUID().toString())
        1 * this.mapper.writeValueAsString(_ as UpdateTableMessage) >> UUID.randomUUID().toString()
        1 * this.client.publish(this.tableArn, _ as String) >> new PublishResult()
        1 * this.registry.createId(Metrics.CounterSNSNotificationTableRename.name()) >> this.id
        1 * this.id.withTags(Metrics.statusSuccessMap) >> this.id
        1 * this.registry.counter(this.id) >> this.counter
        1 * this.registry.timer(
            Metrics.TimerNotificationsPublishDelay.name(),
            "type",
            _ as String
        ) >> this.timer
        1 * this.timer.record(_ as Long, _ as TimeUnit)
    }

    def "Will Notify On Table Update"() {
        def event = new MetacatUpdateTablePostEvent(
            this.qName,
            this.requestContext,
            this,
            new TableDto(),
            new TableDto()
        )

        when:
        this.service.notifyOfTableUpdate(event)

        then:
        2 * this.mapper.valueToTree(_ as TableDto) >> new TextNode(UUID.randomUUID().toString())
        1 * this.mapper.writeValueAsString(_ as UpdateTableMessage) >> UUID.randomUUID().toString()
        1 * this.client.publish(this.tableArn, _ as String) >> new PublishResult()
        1 * this.registry.createId(Metrics.CounterSNSNotificationTableUpdate.name()) >> this.id
        1 * this.id.withTags(Metrics.statusSuccessMap) >> this.id
        1 * this.registry.counter(this.id) >> this.counter
        1 * this.registry.timer(
            Metrics.TimerNotificationsPublishDelay.name(),
            "type",
            _ as String
        ) >> this.timer
        1 * this.timer.record(_ as Long, _ as TimeUnit)
    }

    def "Won't retry on Other Exception"() {
        def event = new MetacatCreateTablePostEvent(
            this.qName,
            this.requestContext,
            this,
            new TableDto()
        )
        def tags = this.qName.parts()
        tags.putAll(Metrics.statusFailureMap)

        when:
        this.service.notifyOfTableCreation(event)

        then:
        1 * this.mapper.writeValueAsString(_ as CreateTableMessage) >> UUID.randomUUID().toString()
        1 * this.client.publish(this.tableArn, _ as String) >> { throw new NotFoundException("Exception") }
        1 * this.registry.createId(Metrics.CounterSNSNotificationTableCreate.name()) >> this.id
        1 * this.id.withTags(tags) >> this.id
        1 * this.registry.counter(this.id) >> this.counter
        1 * this.registry.timer(
            Metrics.TimerNotificationsPublishDelay.name(),
            "type",
            _ as String
        ) >> this.timer
        1 * this.timer.record(_ as Long, _ as TimeUnit)
    }
}
