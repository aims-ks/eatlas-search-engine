package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class DrupalBlockIndexerTest extends IndexerTestBase {

    @Test
    public void testIndexBlocks() throws IOException {
        try (SearchClient client = this.createElasticsearchClient()) {
            Assertions.assertEquals(HealthStatus.Green, client.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            String index = "blocks";
            Messages messages = Messages.getInstance(null);

            client.createIndex(index);

            // Find the indexer, defined in the config file
            DrupalBlockIndexer drupalBlockIndexer =
                    (DrupalBlockIndexer)this.getConfig().getIndexer(index);

            // TODO INDEX
            // Needs to do that, but that loads entities from URL. Need small refactorisation.
            // TODO USE FILE URL!!!
            //drupalBlockIndexer.internalIndex(client, lastHarvested, messages);

            // Wait for ElasticSearch to finish its indexation
            client.refresh(index);

            // TODO CHECK

            Assertions.assertEquals(HealthStatus.Green, client.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }
}
