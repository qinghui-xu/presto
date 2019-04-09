/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.plugin.hive.metastore.thrift;

import io.airlift.units.Duration;
import io.prestosql.spi.PrestoException;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static io.airlift.testing.Assertions.assertContains;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestDiscoveryHiveCluster
{
    private static final HiveMetastoreClient DEFAULT_CLIENT = createFakeMetastoreClient();
    private static final HiveMetastoreClient FALLBACK_CLIENT = createFakeMetastoreClient();

    private static final DiscoveryHiveClusterConfig CONFIG_WITH_FALLBACK = new DiscoveryHiveClusterConfig()
            .setMetastoreUris("thrift://default:8080,thrift://fallback:8090,thrift://fallback2:8090");

    private static final DiscoveryHiveClusterConfig CONFIG_WITHOUT_FALLBACK = new DiscoveryHiveClusterConfig()
            .setMetastoreUris("thrift://default:8080");

    private static final DiscoveryHiveClusterConfig CONFIG_WITH_FALLBACK_WITH_CONSUL = new DiscoveryHiveClusterConfig()
            .setMetastoreUris("consul://default:8080/hive-metastore,thrift://fallback:8090,thrift://fallback2:8090");

    private static final DiscoveryHiveClusterConfig CONFIG_WITHOUT_FALLBACK_WITH_CONSUL = new DiscoveryHiveClusterConfig()
            .setMetastoreUris("consul://default:8080/hive-metastore");

    @Test
    public void testDefaultHiveMetastore()
    {
        HiveCluster cluster = createHiveCluster(CONFIG_WITH_FALLBACK, singletonList(DEFAULT_CLIENT));
        assertEquals(cluster.createMetastoreClient(), DEFAULT_CLIENT);
    }

    @Test
    public void testFallbackHiveMetastore()
    {
        HiveCluster cluster = createHiveCluster(CONFIG_WITH_FALLBACK, asList(null, null, FALLBACK_CLIENT, null, null, FALLBACK_CLIENT));
        assertEquals(cluster.createMetastoreClient(), FALLBACK_CLIENT);
        assertEquals(cluster.createMetastoreClient(), FALLBACK_CLIENT);
    }

    @Test
    public void testFallbackHiveMetastoreFails()
    {
        HiveCluster cluster = createHiveCluster(CONFIG_WITH_FALLBACK, asList(null, null, null));
        assertCreateClientFails(cluster, "Failed connecting to Hive metastore using any of the URI's: [thrift://default:8080, thrift://fallback:8090, thrift://fallback2:8090]");
    }

    @Test
    public void testMetastoreFailedWithoutFallback()
    {
        HiveCluster cluster = createHiveCluster(CONFIG_WITHOUT_FALLBACK, singletonList(null));
        assertCreateClientFails(cluster, "Failed connecting to Hive metastore using any of the URI's: [thrift://default:8080]");
    }

    @Test
    public void testFallbackHiveMetastoreWithConsul()
    {
        HiveCluster cluster = createHiveCluster(CONFIG_WITH_FALLBACK_WITH_CONSUL, asList(null, FALLBACK_CLIENT, null, FALLBACK_CLIENT));
        assertEquals(cluster.createMetastoreClient(), FALLBACK_CLIENT);
        assertEquals(cluster.createMetastoreClient(), FALLBACK_CLIENT);
    }

    @Test
    public void testMetastoreFailedWithoutFallbackWithConsul()
    {
        HiveCluster cluster = createHiveCluster(CONFIG_WITHOUT_FALLBACK_WITH_CONSUL, singletonList(null));
        assertCreateClientFails(cluster, "Failed to resolve Hive metastore addresses: [consul://default:8080/hive-metastore");
    }

    private static void assertCreateClientFails(HiveCluster cluster, String message)
    {
        try {
            cluster.createMetastoreClient();
            fail("expected exception");
        }
        catch (PrestoException e) {
            assertContains(e.getMessage(), message);
        }
    }

    private static HiveCluster createHiveCluster(DiscoveryHiveClusterConfig config, List<HiveMetastoreClient> clients)
    {
        return new DiscoveryHiveCluster(config, new MockHiveMetastoreClientFactory(Optional.empty(), new Duration(1, SECONDS), clients));
    }

    private static HiveMetastoreClient createFakeMetastoreClient()
    {
        return new MockHiveMetastoreClient();
    }
}