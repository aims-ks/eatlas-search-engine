package au.gov.aims.eatlas.searchengine.entity;

import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.index.AbstractDrupalEntityIndexer;
import au.gov.aims.eatlas.searchengine.logger.Level;
import org.json.JSONObject;

import java.net.URL;

public class DrupalBlock extends AbstractDrupalEntity {
    private Integer bid;

    protected DrupalBlock() {
        super();
    }

    // Load from Drupal JSON:API output
    //   http://localhost:1022/jsonapi/block_content/network
    public DrupalBlock(AbstractDrupalEntityIndexer<?> indexer, JSONObject jsonApiBlock, AbstractLogger logger) {
        super(jsonApiBlock, logger);
        this.setIndex(indexer.getIndex());

        if (jsonApiBlock != null) {
            URL publicBaseUrl = AbstractDrupalEntity.getDrupalPublicBaseUrl(indexer, jsonApiBlock, logger);

            // UUID
            this.setId(jsonApiBlock.optString("id", null));

            // Block ID
            JSONObject jsonAttributes = jsonApiBlock.optJSONObject("attributes");
            String bidStr = jsonAttributes == null ? null : jsonAttributes.optString("drupal_internal__id", null);
            this.bid = bidStr == null ? null : Integer.parseInt(bidStr);

            // Title (aka "Description" in Drupal UI, aka "info" in Drupal API)
            this.setTitle(jsonAttributes == null ? null : jsonAttributes.optString("info", null));

            // Last modified
            this.setLastModified(AbstractDrupalEntityIndexer.parseLastModified(jsonApiBlock));

            // Block URL
            String nodeRelativePath = DrupalBlock.getBlockRelativeUrl(jsonApiBlock);
            if (publicBaseUrl != null && nodeRelativePath != null) {
                try {
                    this.setLink(new URL(publicBaseUrl, nodeRelativePath));
                } catch(Exception ex) {
                    logger.addMessage(Level.ERROR,
                            String.format("Can not craft node URL from Drupal base URL: %s", publicBaseUrl), ex);
                }
            }

            // Lang code
            this.setLangcode(jsonAttributes == null ? null : jsonAttributes.optString("langcode", null));
        }
    }

    // TODO Resolve from a template defined in indexer config
    private static String getBlockRelativeUrl(JSONObject jsonApiBlock) {
        // First, look if there is a path alias
        JSONObject jsonAttributes = jsonApiBlock == null ? null : jsonApiBlock.optJSONObject("attributes");
        JSONObject jsonAttributesPath = jsonAttributes == null ? null : jsonAttributes.optJSONObject("path");
        String pathAlias = jsonAttributesPath == null ? null : jsonAttributesPath.optString("alias", null);
        if (pathAlias != null) {
            return pathAlias;
        }

        // Otherwise, return "/node/[NODE ID]"
        String bid = jsonAttributes == null ? null : jsonAttributes.optString("drupal_internal__id", null);
        if (bid != null) {
            return "/block/" + bid; // TODO This URL is WRONG! There is no default URL for blocks
        }

        return null;
    }

    public Integer getBid() {
        return this.bid;
    }

    protected void setBid(Integer bid) {
        this.bid = bid;
    }

    public static DrupalBlock load(JSONObject json, AbstractLogger logger) {
        DrupalBlock block = new DrupalBlock();
        block.loadJSON(json, logger);
        String bidStr = json.optString("bid", null);
        if (bidStr != null && !bidStr.isEmpty()) {
            block.bid = Integer.parseInt(bidStr);
        }

        return block;
    }

    @Override
    public JSONObject toJSON() {
        return super.toJSON()
            .put("bid", this.bid);
    }
}
