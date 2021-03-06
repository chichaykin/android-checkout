/*
 * Copyright 2014 serso aka se.solovyev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * Contact details
 *
 * Email: se.solovyev@gmail.com
 * Site:  http://se.solovyev.org
 */

package org.solovyev.android.checkout;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(CheckoutTestRunner.class)
public class PendingRequestsTest {

	@Test
	public void testThreadSafeness() throws Exception {
		final PendingRequests requests = new PendingRequests();
		final AtomicInteger counter = new AtomicInteger();
		final AtomicInteger expected = new AtomicInteger();
		final Random r = new Random(System.currentTimeMillis());
		final ListUncaughtExceptionHandler eh = new ListUncaughtExceptionHandler();
		final Executor executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				final Thread thread = new Thread(r);
				thread.setUncaughtExceptionHandler(eh);
				return thread;
			}
		});

		for (int i = 0; i < 100000; i++) {
			switch (r.nextInt(4)) {
				case 0:
					expected.incrementAndGet();
					requests.add(newRequest(i + 1, counter, r.nextInt(20)));
					executor.execute(requests);
					break;
				case 1:
					requests.cancel(r.nextInt(i + 1));
					break;
				case 2:
					final int tag = r.nextInt(i + 1) / 10;
					requests.cancelAll(tag);
					break;
				case 3:
					final RequestRunnable request = requests.pop();
					if (request != null) {
						if (((CountingRequest) request).done.compareAndSet(false, true)) {
							counter.incrementAndGet();
						}
					}
					break;
			}
		}
		while (requests.peek() != null) {
			executor.execute(requests);
			Thread.sleep(50);
		}

		if(!eh.exceptions.isEmpty()) {
			throw new AssertionError(eh.exceptions.get(0));
		}
		Assert.assertEquals(expected.get(), counter.get());
	}

	@Nonnull
	private RequestRunnable newRequest(int id, @Nonnull AtomicInteger counter, long sleep) {
		return new CountingRequest(id, counter, sleep);
	}

	private static class CountingRequest implements RequestRunnable {
		private final int id;
		@Nonnull
		private final AtomicInteger counter;
		private final long sleep;
		@Nonnull
		private final AtomicBoolean done = new AtomicBoolean();

		public CountingRequest(int id, @Nonnull AtomicInteger counter, long sleep) {
			this.id = id;
			this.counter = counter;
			this.sleep = sleep;
		}

		@Override
		public int getId() {
			return id;
		}

		@Nullable
		@Override
		public Object getTag() {
			return id / 10;
		}

		@Override
		public void cancel() {
			if (done.compareAndSet(false, true)) {
				counter.incrementAndGet();
			}
		}

		@Nullable
		@Override
		public Request getRequest() {
			return null;
		}

		@Override
		public boolean run() {
			if(!done.compareAndSet(false, true)) {
				return true;
			}
			counter.incrementAndGet();
			try {
				Thread.sleep(sleep);
			} catch (InterruptedException e) {
				Assert.fail(e.getMessage());
			}
			return true;
		}
	}

	private static class ListUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

		@Nonnull
		private final List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<Throwable>());

		@Override
		public void uncaughtException(Thread t, Throwable e) {
			exceptions.add(e);
		}
	}
}