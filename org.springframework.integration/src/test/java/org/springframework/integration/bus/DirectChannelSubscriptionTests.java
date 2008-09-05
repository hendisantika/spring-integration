/*
 * Copyright 2002-2008 the original author or authors.
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

package org.springframework.integration.bus;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.ChannelRegistry;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.channel.ThreadLocalChannel;
import org.springframework.integration.config.annotation.MessagingAnnotationPostProcessor;
import org.springframework.integration.endpoint.AbstractInOutEndpoint;
import org.springframework.integration.endpoint.ServiceActivatorEndpoint;
import org.springframework.integration.message.Message;
import org.springframework.integration.message.MessageMappingMethodInvoker;
import org.springframework.integration.message.MessagingException;
import org.springframework.integration.message.StringMessage;
import org.springframework.integration.util.MethodInvoker;

/**
 * @author Mark Fisher
 */
public class DirectChannelSubscriptionTests {

	private DefaultMessageBus bus = new DefaultMessageBus();

	private DirectChannel sourceChannel = new DirectChannel();

	private ThreadLocalChannel targetChannel = new ThreadLocalChannel();


	@Before
	public void setupChannels() {
		sourceChannel.setBeanName("sourceChannel");
		targetChannel.setBeanName("targetChannel");
		bus.registerChannel(sourceChannel);
		bus.registerChannel(targetChannel);
	}


	@Test
	public void testSendAndReceiveForRegisteredEndpoint() {
		MethodInvoker invoker = new MessageMappingMethodInvoker(new TestBean(), "handle");
		ServiceActivatorEndpoint endpoint = new ServiceActivatorEndpoint(invoker);
		endpoint.setSource(sourceChannel);
		endpoint.setOutputChannel(targetChannel);
		endpoint.setBeanName("testEndpoint");
		bus.registerEndpoint(endpoint);
		bus.start();
		this.sourceChannel.send(new StringMessage("foo"));
		Message<?> response = this.targetChannel.receive();
		assertEquals("foo!", response.getPayload());
		bus.stop();
	}

	@Test
	public void testSendAndReceiveForAnnotatedEndpoint() {
		MessagingAnnotationPostProcessor postProcessor = new MessagingAnnotationPostProcessor(bus);
		postProcessor.afterPropertiesSet();
		TestEndpoint endpoint = new TestEndpoint();
		postProcessor.postProcessAfterInitialization(endpoint, "testEndpoint");
		bus.start();
		this.sourceChannel.send(new StringMessage("foo"));
		Message<?> response = this.targetChannel.receive();
		assertEquals("foo-from-annotated-endpoint", response.getPayload());
		bus.stop();
	}

	@Test(expected=RuntimeException.class)
	public void testExceptionThrownFromRegisteredEndpoint() {
		QueueChannel errorChannel = new QueueChannel();
		errorChannel.setBeanName(ChannelRegistry.ERROR_CHANNEL_NAME);
		bus.registerChannel(errorChannel);
		AbstractInOutEndpoint endpoint = new AbstractInOutEndpoint() {
			public Message<?> handle(Message<?> message) {
				throw new RuntimeException("intentional test failure");
			}
		};
		endpoint.setSource(sourceChannel);
		endpoint.setOutputChannel(targetChannel);
		endpoint.setBeanName("testEndpoint");
		bus.registerEndpoint(endpoint);
		bus.start();
		this.sourceChannel.send(new StringMessage("foo"));
	}

	@Test(expected=MessagingException.class)
	public void testExceptionThrownFromAnnotatedEndpoint() {
		QueueChannel errorChannel = new QueueChannel();
		errorChannel.setBeanName(ChannelRegistry.ERROR_CHANNEL_NAME);
		bus.registerChannel(errorChannel);
		MessagingAnnotationPostProcessor postProcessor = new MessagingAnnotationPostProcessor(bus);
		postProcessor.afterPropertiesSet();
		FailingTestEndpoint endpoint = new FailingTestEndpoint();
		postProcessor.postProcessAfterInitialization(endpoint, "testEndpoint");
		bus.start();
		this.sourceChannel.send(new StringMessage("foo"));
	}


	private static class TestBean {

		public Message<?> handle(Message<?> message) {
			return new StringMessage(message.getPayload() + "!");
		}
	}


	@MessageEndpoint
	public static class TestEndpoint {

		@ServiceActivator(inputChannel="sourceChannel", outputChannel="targetChannel")
		public Message<?> handle(Message<?> message) {
			return new StringMessage(message.getPayload() + "-from-annotated-endpoint");
		}
	}


	@MessageEndpoint
	public static class FailingTestEndpoint {

		@ServiceActivator(inputChannel="sourceChannel", outputChannel="targetChannel")
		public Message<?> handle(Message<?> message) {
			throw new RuntimeException("intentional test failure");
		}
	}

}
