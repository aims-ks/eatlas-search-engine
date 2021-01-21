/*
 *  Copyright (C) 2020 Australian Institute of Marine Science
 *
 *  Contact: Gael Lafond <g.lafond@aims.gov.au>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package au.gov.aims.eatlas.searchengine.rest;

import au.gov.aims.eatlas.searchengine.entity.DrupalNode;
import au.gov.aims.eatlas.searchengine.entity.ExternalLink;
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import au.gov.aims.eatlas.searchengine.entity.GeoServerLayer;
import au.gov.aims.eatlas.searchengine.search.ErrorMessage;
import au.gov.aims.eatlas.searchengine.search.IndexSummary;
import au.gov.aims.eatlas.searchengine.search.SearchResult;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import au.gov.aims.eatlas.searchengine.search.Summary;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Path("/dummysearch/v1")
public class DummySearch {
    private static final Logger LOGGER = Logger.getLogger(DummySearch.class.getName());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(
            @Context HttpServletRequest servletRequest,
            @QueryParam("q") String q,
            @QueryParam("start") Integer start,
            @QueryParam("hits") Integer hits,
            @QueryParam("idx") List<String> idx, // List of indexes used for the summary
            @QueryParam("fidx") List<String> fidx // List of indexes to filter the search results (optional)
    ) {

        LOGGER.log(Level.WARN, "q: " + q);
        LOGGER.log(Level.WARN, "start: " + start);
        LOGGER.log(Level.WARN, "hits: " + hits);
        if (idx != null && !idx.isEmpty()) {
            for (int i=0; i<idx.size(); i++) {
                LOGGER.log(Level.WARN, "idx["+i+"]: " + idx.get(i));
            }
        }
        if (fidx != null && !fidx.isEmpty()) {
            for (int i=0; i<fidx.size(); i++) {
                LOGGER.log(Level.WARN, "fidx["+i+"]: " + fidx.get(i));
            }
        }

        // TODO Do a real search!
        SearchResults results = this.getNoSearchResults(start, hits);

        // Fake search to test search client (Drupal)
        // - "lorem" or "ipsum": Returns 200+ fake search results.
        // - "crash": Returns a 500 server error (or other error code if a 3 digit number >=100 is found in the request).
        // - Everything else: Returns zero search results.
        if (q != null) {
            String lcq = q.toLowerCase();
            if (lcq.contains("lorem") || lcq.contains("ipsum")) {
                results = this.getFakeSearchResults(start, hits, idx, fidx);
            } else if (lcq.contains("crash")) {
                int statusCode = this.findStatusCode(lcq, 500);

                ErrorMessage errorMessage = new ErrorMessage()
                    .setErrorMessage("Requested search engine crash.")
                    .setStatusCode(statusCode);
                return Response.status(statusCode).entity(errorMessage.toString()).build();
            }
        }

        String responseTxt = results.toString();
        LOGGER.log(Level.DEBUG, responseTxt);

        // Disable cache DURING DEVELOPMENT!
        CacheControl noCache = new CacheControl();
        noCache.setNoCache(true);

        // Return the JSON array with a OK status.
        return Response.ok(responseTxt).cacheControl(noCache).build();
    }



    /**
     * @deprecated Used to test, do not forget to delete!
     */
    private int findStatusCode(String query, int defaultValue) {
        Pattern p = Pattern.compile("\\d+");
        Matcher m = p.matcher(query);
        while (m.find()) {
            String strNumber = m.group();
            if (strNumber.length() == 3) {
                int statusCode = Integer.parseInt(strNumber);
                if (statusCode >= 100) {
                    return statusCode;
                }
            }
        }

        return defaultValue;
    }

    /**
     * @deprecated Used to test, do not forget to delete!
     */
    private SearchResults getNoSearchResults(Integer start, Integer hits) {
        SearchResults results = new SearchResults();

        results.setSummary(new Summary()
            .setHits(0L)
        );

        return results;
    }

    private SearchResults getFakeSearchResults(Integer start, Integer hits, List<String> idx, List<String> fidx) {
        // Get every single fake search results, even the one for indexes the website do not care about
        List<SearchResult> resultList = null;
        try {
            resultList = this.getAllFakeSearchResults();
        } catch(Exception ex) {
            ex.printStackTrace();
            return null;
        }

        // Filter search results

        // Filter indexes (all searchable indexes)
        if (idx != null && !idx.isEmpty()) {
            List<SearchResult> newResultList = new ArrayList<SearchResult>();
            for (SearchResult result : resultList) {
                if (idx.contains(result.getIndex())) {
                    newResultList.add(result);
                }
            }
            resultList = newResultList;
        }

        // Filter indexes (for current search)
        List<SearchResult> filteredResultList = resultList;
        if (fidx != null && !fidx.isEmpty()) {
            filteredResultList = new ArrayList<SearchResult>();
            for (SearchResult result : resultList) {
                if (fidx.contains(result.getIndex())) {
                    filteredResultList.add(result);
                }
            }
        }

        // Filter page

        // Trim the results, by doing a sequential search.
        // That's pretty bad, but that method is a mockup...
        List<SearchResult> newResultList = new ArrayList<SearchResult>();
        int intStart = 0;
        int intHits = 10;

        if (start != null) {
            intStart = start;
        }
        if (hits != null) {
            intHits = hits;
        }

        for (int i = intStart; i < Math.min(intStart + intHits, filteredResultList.size()); i++) {
            newResultList.add(filteredResultList.get(i));
        }


        SearchResults results = new SearchResults();
        results.setSearchResults(newResultList);

        results.setSummary(new Summary()
            .setHits((long)resultList.size())

            .putIndexSummary(new IndexSummary()
                .setIndex("eatlas_article")
                .setHits(this.countIndexResults(resultList, "eatlas_article")))
            .putIndexSummary(new IndexSummary()
                .setIndex("eatlas_extlink")
                .setHits(this.countIndexResults(resultList, "eatlas_extlink")))
            .putIndexSummary(new IndexSummary()
                .setIndex("eatlas_layer")
                .setHits(this.countIndexResults(resultList, "eatlas_layer")))
            .putIndexSummary(new IndexSummary()
                .setIndex("eatlas_metadata")
                .setHits(this.countIndexResults(resultList, "eatlas_metadata")))
            .putIndexSummary(new IndexSummary()
                .setIndex("eatlas_broken")
                .setHits(this.countIndexResults(resultList, "eatlas_broken")))
        );

        return results;
    }

    private long countIndexResults(List<SearchResult> resultList, String index) {
        long count = 0;
        if (index != null) {
            index = index.trim();
            if (!index.isEmpty()) {
                for (SearchResult result : resultList) {
                    if (index.equals(result.getIndex())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Return a list of results
     */
    private List<SearchResult> getAllFakeSearchResults() throws MalformedURLException {
        List<SearchResult> results = new ArrayList<SearchResult>();

        ExternalLink brokenLink = new ExternalLink(
            "https://broken.bad/should/not/appear/in/search/results/",
            "https://lh3.googleusercontent.com/proxy/f9semJrOD1mcB78d_iD90HJvjKhmrYv5c5n3HGYq8nxFkHs_5gd_8P5IomW3JfkNOw8XUvUBUBc4VSaDd52MYnhvzQ",
            "eatlas_broken"
        );
        brokenLink.setDocument("BROKEN DOCUMENT");
        brokenLink.setLangcode("en");
        results.add(new SearchResult()
            .setEntity(brokenLink.toJSON())
            .setIndex("eatlas_broken")
            .setScore(23)
        );

        DrupalNode drupalNode = new DrupalNode(null, null);
        drupalNode.setLink(new URL("http://localhost:9090/node/4"));
        drupalNode.setTitle("A guide to Indigenous science, management and governance of Australian coastal waters");
        drupalNode.setDocument(
                "<p>Understanding the <a href=\"http://www.ozcoasts.gov.au/pdf/CRC/Coastal_Management_in_Australia.pdf\" target=\"_blank\">management and governance of Australia’s vast coastline</a> can be complex. International, Commonwealth, State and Indigenous entities all have various roles and powers to promote the health and integrity of Australia’s marine environments.</p>" +
                "<p style=\"text-align: center;\"><em>[<strong>Quick links:</strong> <a href=\"#Promoting partnerships\">Promoting</a> Partnerships - <a href=\"#maps\">Maps</a> to help you get started - <a href=\"#links\">Links</a> to Indigenous Sea country Management Plans]</em></p>" +
                "<div style=\"float : left;\">[IMAGE]</div>" +
                "<p>For <a href=\"https://australian.museum/about/history/exhibitions/indigenous-australians/\" target=\"_blank\">Indigenous Australians</a>, the coastlines where they live not only play a significant role in their daily lives by providing natural resources, but are also deeply embedded in their social, cultural and spiritual values. Indigenous Australians have been skilfully managing their “<a href=\"http://www.gbrmpa.gov.au/__data/assets/pdf_file/0010/4798/gbrmpa_ReefBeat_2010SCC_2.pdf\" target=\"_blank\">Sea country</a>” for thousands of years. However, modern day coastal Australia is facing <a href=\"https://www.environment.gov.au/system/files/pages/743e805c-4a49-42a0-88c1-0b6f06eaec0e/files/soe2011-report-marine-environment-3-pressures.pdf\" target=\"_blank\">rapidly increasing pressures</a> from many new sources. Population growth, recreational and commercial fishing, industry and the rapid development of coastal areas are all examples.</p>" +
                "<div style=\"float : right;\">[IMAGE]</div>" +
                "<p>In response to today’s modern day challenges, many Indigenous groups have developed their own management goals and aspirations, many of which are synthesised in Healthy Country (<a href=\"https://www.healthinfonet.ecu.edu.au/key-resources/programs-projects?pid=2339\" target=\"_blank\">see an example</a>) and Indigenous Protected Area Management Plans (IPAMPs - <a href=\"http://www.clcac.com.au/publications/2015/75\" target=\"_blank\">see an example</a>). One goal within most of these plans is to form strategic alliances and partnerships with other marine science and management agencies, a goal that provides everyone with the opportunity to work together for the common good. There is a shared realisation that the integration of traditional knowledge and western science provides a potent way in which to better understand our coastline ecosystems and meet these new challenges.</p>" +
                "<p>But how do you go about conducting science in indigenous Sea country? Who lives where? What is the status of governance? Who manages what? And with whom can we partner?</p>" +
                "<p>&nbsp;</p>" +
                "<h2><a id=\"Promoting partnerships\" name=\"Promoting partnerships\"></a>Promoting partnerships for Sea Country Research and Monitoring in Western Australia: A snapshot of Indigenous, science and management agency partners</h2>" +
                "<p>Indigenous Sea Country science managed by Land &amp; Sea Rangers in association with one or more science, university, industry and management agencies is a growing area in the marine research and monitoring science fields. In Western Australia, these types of partnerships have grown exponentially in the last decade alongside Native Title Claims and are proving effective in delivering cross-cultural understanding in areas that include not only science but marine management, business and education sectors. In all, thirty saltwater Native Title holders and claimants, eight government agencies, four universities and various marine science and professional organisations have developed or are in the process of developing cross-cultural partnerships specifically targeting marine science projects. Alongside this positive outcome, the sharing and blending of Traditional Ecological Knowledge with western science techniques is proving to be an effective, respectful and inclusive way to acknowledge both knowledge streams.</p>" +
                "<p>A report titled “<em><a href=\"https://www.nespmarine.edu.au/system/files/Lincoln_Hedge%20Promoting%20partnerships%20for%20Sea%20Country_FINAL%2001Nov19.pdf\" target=\"_blank\">Promoting partnerships for Sea Country Research and Monitoring in Western Australia: A snapshot of Indigenous, science and management agency partners</a>”</em> provides a current state-of-affairs in listing the present status of indigenous and non-indigenous partners in science, their activities, capacity and direction in collaborative science projects on Saltwater Country in Western Australia. It uses 2019 (August) information to profile a broad range of organisations whose goals, structures, policies and programs will change with time. As such, the information in this document is expected to become progressively outdated. The report has been produced by Mosaic Environmental in conjunction with the National Environmental Science Program’s Marine Biodiversity Hub with the intention of identifying existing partners, as an aid to all interested parties in facilitating further science and management projects and in acknowledgement of the importance of working as a united front when confronting contemporary problems in today’s rapidly evolving marine estates.</p>" +
                "<p>When using this document, please cite appropriately and adhere to the Copyright conditions and Limitation listed below.</p>" +
                "<p><strong>Citation:</strong> Lincoln G, Hedge P (2019) Promoting partnerships for Sea Country Research and Monitoring in Western Australia: A snapshot of Indigenous, science and management agency partners, Version 1. Report to the National Environmental Science Program, Marine Biodiversity Hub. Mosaic Environmental.</p>" +
                "<p><strong>Copyright:</strong> This report is licenced by the University of Tasmania for use under a Creative Commons Attribution 4.0 Australia Licence. For licence conditions, see <a href=\"https://creativecommons.org/licences/by/4.0/\" target=\"_blank\">https://creativecommons.org/licences/by/4.0/</a></p>" +
                "<p><strong>Limitations:</strong> This document should only be used for the purposes of promoting collaborative saltwater science projects between named Indigenous saltwater groups in Western Australia and those included organisations/agencies/collaborations with marine science agendas in the state.</p>" +
                "<p>&nbsp;</p>" +
                "<h2 id=\"maps\">Maps to help you get started...</h2>" +
                "<p>Below we provide links to a set of interactive maps which offer a starting point to guide scientists, managers, students and the general public to identify indigenous Sea country areas. These maps provide basic but important information and are meant to be a first step in identifying the legal framework and governance of traditional indigenous Sea country. Depending on the map, by zooming in on your geographic area of interest, information which identifies traditional ownership, representative bodies, legal status, land-use agreements, landmarks, correct names and GPS locations are all available to guide you so that you can plan ahead, make contact with the correct organisation or simply learn more about indigenous Australia. Click on each subheading below to access the relevant map.</p>" +
                "<p>&nbsp;</p>" +
                "<h2><a href=\"https://northwestatlas.org/node/1709\">Where does native title exist in Australia?</a></h2>" +
                "<div style=\"float : left;\">[IMAGE]</div>" +
                "<p><a href=\"http://www.nntt.gov.au/Information%20Publications/Native%20Title%20an%20overview.pdf\" target=\"_blank\">Native title</a> is the recognition in Australian law that some <a href=\"https://australian.museum/about/history/exhibitions/indigenous-australians/\" target=\"_blank\">Indigenous</a> people continue to hold rights to their land and waters. It may include the right to possess and occupy an area to the exclusion of all others, or it may be a set of non-exclusive rights. There can be no native title rights to minerals, gas or petroleum recognised under Australian law. In tidal and sea areas, only non-exclusive native title can be recognised.</p>" +
                "<p>&nbsp;</p>" +
                "<h2><a href=\"https://northwestatlas.org/node/1703\">Where are Indigenous Protected Areas (IPAs) in Australia?</a></h2>" +
                "<div style=\"float : right;\">[IMAGE]</div>" +
                "<p><a href=\"https://www.environment.gov.au/land/indigenous-protected-areas\" target=\"_blank\">Indigenous Protected Areas</a> (IPAs) in Australia are voluntarily dedicated by Indigenous groups on Indigenous owned or managed land or sea country. They are recognised by the Australian Government as an important part of the <a href=\"https://www.environment.gov.au/land/nrs\" target=\"_blank\">National Reserve System</a>, protecting the nation's <a href=\"https://australian.museum/learn/science/biodiversity/what-is-biodiversity/\" target=\"_blank\">biodiversity</a> for the benefit of all Australians. There are currently over 70 dedicated IPAs across 65 million hectares accounting for more than 40% of the National Reserve System's total area.</p>" +
                "<p>&nbsp;</p>" +
                "<h2><a href=\"https://northwestatlas.org/node/1704\">Where do Indigenous Land Use Agreements (ILUA) exist across Australia?</a></h2>" +
                "<div style=\"float : left;\">[IMAGE]</div>" +
                "<p>An <a href=\"http://www.nntt.gov.au/ILUAs/Pages/default.aspx\" target=\"_blank\">Indigenous Land Use Agreement</a> (ILUA) is an agreement about the use and management of land and waters made between people who hold, or may hold, native title in the area, and other people, organisations or governments. To be an ILUA, an agreement must meet with the requirements of the <a href=\"https://www.legislation.gov.au/Details/C2012C00273\" target=\"_blank\">Native Title Act 1993</a>.</p>" +
                "<p>&nbsp;</p>" +
                "<h2><a href=\"https://northwestatlas.org/node/1705\">Where do Representative Aboriginal and Torres Strait Islander Body (RATSIB) areas exist across Australia?</a></h2>" +
                "<div style=\"float : right;\">[IMAGE]</div>" +
                "<p>The <a href=\"http://www.nntt.gov.au/Pages/Home-Page.aspx\" target=\"_blank\">National Native Title Tribunal</a> was established by the <a href=\"https://www.legislation.gov.au/Details/C2012C00273\" target=\"_blank\">Native Title Act 1993</a> to make decisions, conduct inquiries, reviews and mediations, and assist various parties with <a href=\"http://www.nntt.gov.au/Information%20Publications/Native%20Title%20an%20overview.pdf\" target=\"_blank\">native </a><a href=\"http://www.nntt.gov.au/Information%20Publications/Native%20Title%20an%20overview.pdf\" target=\"_blank\">title</a> applications, and Indigenous land use agreements (‘<a href=\"http://www.nntt.gov.au/ILUAs/Pages/stepstoilua.aspx\" target=\"_blank\">ILUA</a>s’). Australia is split into representative areas to facilitiate this.</p>" +
                "<p>&nbsp;</p>" +
                "<h2><a href=\"https://northwestatlas.org/node/1706\">What are the Preferred and Alternate names for indigenous locations where Australian Government programs and services have been, are being, or may be provided?</a></h2>" +
                "<div style=\"float : left;\">[IMAGE]</div>" +
                "<p>The Australian Government developed its <a href=\"https://data.gov.au/dataset/agil-dataset\" target=\"_blank\">Indigenous Programs &amp; Policy Locations (AGIL) dataset</a> as an authoritative source of indigenous location names across Australia. It is designed to support the accurate positioning, consistent reporting, and effective delivery of Australian Government programs and services to indigenous locations. The dataset contains Preferred and Alternate names for indigenous locations where Australian Government programs and services have been, are being, or may be provided. This dataset is NOT a complete listing of all locations at which indigenous people reside. Town and city names are not included in the dataset.</p>" +
                "<p>&nbsp;</p>" +
                "<h3 id=\"links\">Links to Indigenous Sea country Management Plans...</h3>" +
                "<p>Where established indigenous Sea country Management Plans are in place (<a href=\"https://northwestatlas.org/node/1703#map\">view a map</a>), you can view them by clicking on their names in the tables below. A separate table is provided for <a href=\"#WA_table\">Western Australia</a>, <a href=\"#NT_table\">Northern Territory</a>, <a href=\"#QLD_table\">Queensland</a>, <a href=\"#Torres Strait Islands\">Torres Strait Islands</a>, <a href=\"#New South Wales\">New South Wales</a>, <a href=\"#Victoria\">Victoria</a>, <a href=\"#Tasmania\">Tasmania</a>, and <a href=\"#South Australia\">South Australia</a>.</p>" +
                "<table align=\"center\" border=\"1\" cellpadding=\"5\" cellspacing=\"0\" id=\"WA_table\">" +
                "	<caption>" +
                "	<h3>Western Australia (<a href=\"https://northwestatlas.org/node/1703#map\">view a map</a>)</h3>" +
                "	</caption>" +
                "	<thead>" +
                "		<tr>" +
                "			<th scope=\"col\">Indigenous Protected Area</th>" +
                "			<th scope=\"col\">Name of plan</th>" +
                "			<th scope=\"col\">Docment type</th>" +
                "			<th scope=\"col\">Time span</th>" +
                "			<th scope=\"col\">Source agency</th>" +
                "		</tr>" +
                "	</thead>" +
                "	<tbody>" +
                "		<tr>" +
                "			<td>Nyangumarta Warrarn and Karajarri IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/WA/1. EIGHTY_MILE_BEACH_MGT_PLAN_V12 Ngarla-Nyanguarta-Karajarri.pdf\" target=\"_blank\">Eighty Mile Beach Marine Park</a></td>" +
                "			<td>State management plan</td>" +
                "			<td>2014-2024</td>" +
                "			<td><a href=\"https://www.dpaw.wa.gov.au/\" target=\"_blank\">DPaW</a>, Nyangumarta Rangers</td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Karajarri IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/WA/2. IPA 64 karajarri healthy country plan 2013-2023.pdf\" target=\"_blank\">Karajarri Healthy Country Plan 2013-2023</a></td>" +
                "			<td>Indigenous Healthy Country Plan</td>" +
                "			<td>2013-2023</td>" +
                "			<td><a href=\"https://www.karajarri.org/\" target=\"_blank\">Karajarri</a> Traditional Lands Association</td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Yawuru IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/WA/3. D Yawuru ynrbmp_mangement_plan_2016.pdf\" target=\"_blank\">Yawuru Nagulagun / Roebuck Bay Marine Park</a></td>" +
                "			<td>Joint Management Plan</td>" +
                "			<td>n/a</td>" +
                "			<td><a href=\"https://www.dpaw.wa.gov.au/\" target=\"_blank\">DPaW</a> &amp; <a href=\"https://www.yawuru.com/our-organisation/land-sea/\" target=\"_blank\">Yawuru</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Bardi Jawi IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/WA/4. IPA 56 bardi-jawi-healthy-country-plan 2013-2023.pdf\" target=\"_blank\">Bardi-Jawi Indigenous Protected Area Management Plan</a></td>" +
                "			<td>Indigenous Healthy Country Plan<indigenous management=\"\" plan=\"\"> </indigenous></td>" +
                "			<td>2013-2023</td>" +
                "			<td><a href=\"https://www.nailsma.org.au/hub/working-together/group/bardi-jawi-rangershtml.html\" target=\"_blank\">Bardi and Jawi Rangers</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Dambimangari IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/WA/5. IPA 59 Dambimangari_Healthy_Country_Plan_2012-2022.pdf\" target=\"_blank\">Dambimangari Healthy Country Plan 2012-2022</a></td>" +
                "			<td>Indigenous Healthy Country Plan</td>" +
                "			<td>2012-2022</td>" +
                "			<td><a href=\"https://www.dpaw.wa.gov.au/\" target=\"_blank\">DPaW</a> &amp; <a href=\"https://www.dambimangari.com.au/\" target=\"_blank\">Dambimangari</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Dambimangari IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/WA/6. Dambi &amp; WG Lalang-garram_Camden_Sound_Marine_Park_MP_2013-2023_WEB.pdf\" target=\"_blank\">Lalang / Garram / Camden Sound Marine Park Management Plan 73 2013-2023</a></td>" +
                "			<td>Joint Management Plan</td>" +
                "			<td>2013-2023</td>" +
                "			<td><a href=\"https://www.dpaw.wa.gov.au/\" target=\"_blank\">DPaW</a>, <a href=\"https://www.dambimangari.com.au/\" target=\"_blank\">Dambimangari</a> &amp; <a href=\"https://www.wunambalgaambera.org.au/\" target=\"_blank\">Wunambal Gaambera Aboriginal Corporation</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Uunguu IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/WA/6. IPA 42 WG Uunguu Rangers Saltwater+Management+Plan 2016-2020.pdf\" target=\"_blank\">Uunguu Indigenous Protected Area: Wundaagu (saltwater) Indicative Plan of Management 2016-2020</a></td>" +
                "			<td>Indigenous Healthy Country Plan</td>" +
                "			<td>2016-2020</td>" +
                "			<td><a href=\"https://www.wunambalgaambera.org.au/\" target=\"_blank\">Wunambal Gaambera Aboriginal Corporation</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Uunguu IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/WA/WA_2_Uunguu_Wunambal_Gaambera_HealthyCountryPlan_2010-2020.pdf\" target=\"_blank\">Wunanbal Gaambera Healthy Country Plan</a></td>" +
                "			<td>Indigenous Healthy Country Plan</td>" +
                "			<td>n/a</td>" +
                "			<td><a href=\"https://www.bushheritage.org.au/places-we-protect/western-australia/wunambal-gaambera\" target=\"_blank\">Wunambal Gaambera Aboriginal Corporation</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Balanggarra IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/WA/7. IPA 60 balanggarra-healthy-country-plan-2012-2022.pdf\" target=\"_blank\">Balanggarra Healthy Country Plan 2012-2022</a></td>" +
                "			<td>Indigenous Health Country Plan</td>" +
                "			<td>2012-2022</td>" +
                "			<td><a href=\"https://www.nativetitle.org.au/profiles/profile_wa_balanggarra.html\" target=\"_blank\">Balanggarra Rangers</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Nyikina Mangala Rangers (Healthy Country Plan)</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/WA/WA_3_Walalakoo_Healthy_Country_Plan_2017_2027_Nyikina%20Mangala%20IPA.pdf\" target=\"_blank\">Walalakoo Healthy Country Plan 2017-2027</a></td>" +
                "			<td>Indigenous Health Country Plan</td>" +
                "			<td>2017-2027</td>" +
                "			<td><a href=\"https://www.walalakoo.org.au/\" target=\"_blank\">Walalakoo Aboriginal Corporation</a>, Nyikina Mangala Rangers</td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Nyul Nyul Rangers (Healthy Country Plan)</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/WA/WA1_Nyul-Nyul-freshwater-management-and-monitoring-plan.pdf\" target=\"_blank\">Nyul Nyul Freshwater Management and Monitoring Plan</a></td>" +
                "			<td>Management and Monitoring Plan</td>" +
                "			<td>n/a</td>" +
                "			<td><a href=\"https://www.nailsma.org.au/hub/working-together/group/nyul-nyul-rangers.html\" target=\"_blank\">Nyul Nyul Rangers</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Nyangumarta Warrarn IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/WA/WA1_Nyangumarta Warrarn Aboriginal Corporation, Yamatji Marlpa Aboriginal Corporation - 2015 - Nyangumarta Warrarn Indigenous Protected Area.pdf\" target=\"_blank\">Nyangumarta Warrarn Indigenous Protected Area Plan of Management 2015 to 2020</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2015-2020</td>" +
                "			<td><a href=\"https://ymac.org.au/tag/nyangumarta-ipa/\" target=\"_blank\">Nyangumarta Warrarn Aboriginal Corporation and Yamatji Marlpa Aboriginal Corporation</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Mayala Baaliboor – Mayala (Country Plan)</td>" +
                "			<td><a href=\"https://static1.squarespace.com/static/59fecece017db2ab70aa1874/t/5db0f27b69c8dc5b926ff7ea/1571877610368/Mayala_Country_Plan_final_email_version.pdf\">2019-2029 Mayala Country Plan</a></td>" +
                "			<td>Country Plan</td>" +
                "			<td>2019-2029</td>" +
                "			<td>" +
                "			<p><a href=\"https://www.nativetitle.org.au/find/pbc/9067\">Mayala Inninalang Aboriginal Corporation</a></p>" +
                "			</td>" +
                "		</tr>" +
                "	</tbody>" +
                "</table>" +
                "<p>&nbsp;</p>" +
                "<table align=\"center\" border=\"1\" cellpadding=\"5\" cellspacing=\"0\" id=\"NT_table\">" +
                "	<caption>" +
                "	<h3>Northern Territory (<a href=\"https://northwestatlas.org/node/1703#map\">view a map</a>)</h3>" +
                "	</caption>" +
                "	<thead>" +
                "		<tr>" +
                "			<th scope=\"col\">Indigenous Protected Area</th>" +
                "			<th scope=\"col\">Name of plan</th>" +
                "			<th scope=\"col\">Docment type</th>" +
                "			<th scope=\"col\">Time span</th>" +
                "			<th scope=\"col\">Source agency</th>" +
                "		</tr>" +
                "	</thead>" +
                "	<tbody>" +
                "		<tr>" +
                "			<td>Anindilyakwa IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/NT/Anindilyakwa-IPA-Management-Plan.pdf\" target=\"_blank\">Anindilyakwa Indigenous Protected Area Plan of Management 2016</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2016-2026</td>" +
                "			<td>" +
                "			<p><a href=\"https://anindilyakwa.com.au/\" target=\"_blank\">Anindilyakwa Land Council</a></p>" +
                "			</td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Dhimurru IPA</td>" +
                "			<td>Dhimurru Yolnuwa Monuk Gapu Wana IPA Sea Country Management Plan 2013-2015</td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2013-2015</td>" +
                "			<td><a href=\"http://www.dhimurru.com.au/\" target=\"_blank\">Dhimurru Aboriginal Corporation</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Dhimurru IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/NT/3. IPA 13 Dhimurru_ipa_management_plan_2015-22.pdf\" target=\"_blank\">Dhimurra Indigenous Protected Area Management Plan 2015-2022</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2015-2022</td>" +
                "			<td><a href=\"http://www.dhimurru.com.au/\" target=\"_blank\">Dhimurru Aboriginal Corporation</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Laynhapuy IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/NT/4. IPA 21 Yirralka Rangers Business Plan 2013-2016.pdf\" target=\"_blank\">Yirralka Rangers Business Plan 2013-2016</a></td>" +
                "			<td>Indigenous strategic business plan</td>" +
                "			<td>2013-2016</td>" +
                "			<td><a href=\"https://www.laynhapuy.com.au/\" target=\"_blank\">Laynhapuy Homelands Aboriginal Corp</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Anindilyakwa IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/NT/5. IPA 20 ALC-15-YEAR-STRATEGIC-PLAN-FINAL 2012-2027.pdf\" target=\"_blank\">ALC 15 year Strategic Plan</a></td>" +
                "			<td>Indigenous Strategic plan</td>" +
                "			<td>2012-2027</td>" +
                "			<td><a href=\"https://anindilyakwa.com.au/\" target=\"_blank\">Anindilyakwa Land Council</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>South East Arnhem Land IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/NT/6. Yugul Mangi Rangers.pdf\" target=\"_blank\">Yugul Mangi Land and Sea Management Corporation</a></td>" +
                "			<td>Indigenous Information</td>" +
                "			<td>n/a</td>" +
                "			<td><a href=\"https://www.anu.edu.au/\" target=\"_blank\">ANU</a> &amp; <a href=\"https://caepr.anu.edu.au/poc/partners/Yugulmangi.php\" target=\"_blank\">Yugul Mangi Sea Management Corp</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Yanyuwa IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/NT/7. IPA 47 Yanyuwa Sea Country Plan.pdf\" target=\"_blank\">Barni-Wardimantha Awara Yanyuwa Sea Country Plan</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2007</td>" +
                "			<td><a href=\"https://en.wikipedia.org/wiki/Yanyuwa_people\" target=\"_blank\">Yanyuwa</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>South East Arnhem Land IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/NT/NT3_South_East_Arnhem_IPA_2016-2021.pdf\" target=\"_blank\">South East Arnhem Land Indigenous Protected Area Plan of Management 2016-2021</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2016-2021</td>" +
                "			<td><a href=\"https://www.nlc.org.au/our-land-sea/caring-for-country/ranger-program\" target=\"_blank\">Yugul Mangi &amp; Numbulwar Numburindi Rangers</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Djelk IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/NT/NT1_Djelk_Healthy_Country_Plan.pdf\" target=\"_blank\">Djelk Healthy Country Plan 2015-2025</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2015-2025</td>" +
                "			<td><a href=\"https://djelkrangers.com\" target=\"_blank\">Djelk Rangers</a> &amp; <a href=\"https://www.bawinanga.com\" target=\"_blank\">Bawinanga Aboriginal Corporation</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Marthakal IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/NT/NT2_Marthakal_IPA_Monitoring_Evaluation_Reporting_Improvement_Plan.pdf\" target=\"_blank\">Marthakal Indigenous Protected Area Monitoring, Evaluation, Reporting and Improvement Plan</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2011-2016</td>" +
                "			<td><a href=\"https://nailsma.org.au/hub/working-together/group/gumurr-marthakal-rangers.html\" target=\"_blank\">Marthakal IPA &amp; Gumurr Marthakal Rangers</a></td>" +
                "		</tr>" +
                "	</tbody>" +
                "</table>" +
                "<p>&nbsp;</p>" +
                "<table align=\"center\" border=\"1\" cellpadding=\"5\" cellspacing=\"0\" id=\"QLD_table\">" +
                "	<caption>" +
                "	<h3>Queensland (<a href=\"https://northwestatlas.org/node/1703#map\">view a map</a>)</h3>" +
                "	</caption>" +
                "	<thead>" +
                "		<tr>" +
                "			<th scope=\"col\">Indigenous Protected Area</th>" +
                "			<th scope=\"col\">Name of plan</th>" +
                "			<th scope=\"col\">Docment type</th>" +
                "			<th scope=\"col\">Time span</th>" +
                "			<th scope=\"col\">Source agency</th>" +
                "		</tr>" +
                "	</thead>" +
                "	<tbody>" +
                "		<tr>" +
                "			<td>Nijinda Durlga IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/QLD/1. IPA 65 Nijinda Durlga IPA (gangalidda) management_plan.pdf\" target=\"_blank\">Nijinda Durlga (Gangalidda) Indigenous Protected Area Management Plan</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>n/a</td>" +
                "			<td><a href=\"http://www.gangalidda-garawa.com.au/\" target=\"_blank\">Gangalidda</a> &amp; <a href=\"http://www.clcac.com.au/land-sea/rangers/gangalidda-garawa\" target=\"_blank\">Gangalidda-Garawa Rangers</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Thuwathu-Bujimulla (Wellesley Islands) IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/QLD/2. IPA 61 Thuwathu-Bujimulla (Wellesley Islands) IPA_management_plan.pdf\" target=\"_blank\">Thuwathu/Bujimulla Indigenous Protected Area Management Plan</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>Varies</td>" +
                "			<td><a href=\"https://www.qld.gov.au/atsi/cultural-awareness-heritage-arts/community-histories-mornington-island/\" target=\"_blank\">Lardil, Yangkal, Kaiadilt</a> &amp; <a href=\"http://www.gangalidda-garawa.com.au/\" target=\"_blank\">Gangalidda</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Pormpuraaw Rangers (Land in Trust)</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/QLD/5. Pormpuraaw Rangers Mt Plan 2010-2015.pdf\" target=\"_blank\">Pormpuraaw Land and Sea Country CNRM Plan 2010-2015</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2010-2015</td>" +
                "			<td>Kuuk Thaayorre &amp; Wik Mungkan</td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Eastern Kuku Yalanji IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/QLD/7. IPA 55 Eastern Kuku Yalanji Jabalina Rangers 2012.pdf\" target=\"_blank\">Eastern Kuku Yalanji Indigenous Protected Area Management Plan Stage 2 - Jalunjii-Warra Land and Sea Country</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2012+</td>" +
                "			<td><a href=\"https://en.wikipedia.org/wiki/Kuku_Yalanji\" target=\"_blank\">Yalanji, Kuku</a> &amp; <a href=\"http://www.jabalbina.com.au/\" target=\"_blank\">Jabalbina Yalanii Aboriginal Corporation</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Mandingalbay IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/QLD/YidinjiIPA.ppt\" target=\"_blank\">Mandingalbay Yidinji IPA Planning</a></td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/QLD/QLD1_Mandingalbay_Yidinji_Fact_Sheet.pdf\" target=\"_blank\">Fact sheet</a></td>" +
                "			<td>n/a</td>" +
                "			<td><a href=\"https://www.djunbunji.com.au/mandingalbay-yidinji-corporation/\" target=\"_blank\">Mandingalbay Yidinji</a> &amp; <a href=\"https://www.djunbunji.com.au/\" target=\"_blank\">Djunbunji Limited</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Mandingalbay IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/QLD/9. IPA 50 Mandingalbay_Plan 2009.pdf\" target=\"_blank\">Strategic Plan for Mandingalbay Yidinji Country</a></td>" +
                "			<td>Indigenous Strategic Plan</td>" +
                "			<td>2009+</td>" +
                "			<td><a href=\"https://www.djunbunji.com.au/mandingalbay-yidinji-corporation/\" target=\"_blank\">Mandingalbay Yidinji</a> &amp; <a href=\"https://www.djunbunji.com.au/\" target=\"_blank\">Djunbunji Limited</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Gunggandii Land and Sea Rangers (Prescribed Body Corporate Aborginal Corporation)</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/QLD/10. Gunggandji-plan-2013.pdf\" target=\"_blank\">Gunggandji Land and Sea Country Plan</a></td>" +
                "			<td>Indigenous Strategic Plan</td>" +
                "			<td>2013+</td>" +
                "			<td><a href=\"https://nativetitle.org.au/profiles/profile_qld_gunggandji.html\" target=\"_blank\">Gunggandji</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Girrigngun IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/QLD/11. IPA 57 Girringun PLAN-OF-MANAGEMENT-2013-2023.pdf\" target=\"_blank\">Girringun Region Indigenous Protected Areas Management Plan 2013-2023</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2013-2023</td>" +
                "			<td><a href=\"https://girringun.com.au/\" target=\"_blank\">Girringun</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Eastern Kuku Yalanji, Mandingalbay and Girringun IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/QLD/Wet Tropics Aboriginal-Cultural-and-NRM-Plan 2005.pdf\" target=\"_blank\">Wet Tropics Aboriginal Cultural and Natural Resource Management Plan</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2005+</td>" +
                "			<td>" +
                "			<p>Varied</p>" +
                "			</td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Guanaba IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/QLD/QLD1_fact sheet-guanaba IPA.pdf\" target=\"_blank\">Guanaba Indigenous Protected Area Fact Sheet</a></td>" +
                "			<td>Fact Sheet Information</td>" +
                "			<td>n/a</td>" +
                "			<td><a href=\"https://www.facebook.com/ngarangwal/\" target=\"_blank\">Ngarang-Wal Gold Coast Aboriginal Association Incorporated</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Kaanju Ngaachi Wenlock and Pascoe Rivers IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/QLD/QLD2_Wenlock and Pascoe Rivers Cape York IPA Management Plan.pdf\" target=\"_blank\">Kaanju Ngaachi Wenlock and Pascoe Rivers Cape York Peninsula Indigenous Protected Area Management Plan</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2005</td>" +
                "			<td><a href=\"https://www.kaanjungaachi.com.au\" target=\"_blank\">Chuulangun Aboriginal Corporation</a></td>" +
                "		</tr>" +
                "	</tbody>" +
                "</table>" +
                "<p>&nbsp;</p>" +
                "<table align=\"center\" border=\"1\" cellpadding=\"5\" cellspacing=\"0\" id=\"Torres Strait Islands\">" +
                "	<caption>" +
                "	<h3>Torres Strait Islands (<a href=\"https://northwestatlas.org/node/1703#map\">view a map</a>)</h3>" +
                "	</caption>" +
                "	<thead>" +
                "		<tr>" +
                "			<th scope=\"col\">Indigenous Protected Area</th>" +
                "			<th scope=\"col\">Name of plan</th>" +
                "			<th scope=\"col\">Document type</th>" +
                "			<th scope=\"col\">Time span</th>" +
                "			<th scope=\"col\">Source agency</th>" +
                "		</tr>" +
                "	</thead>" +
                "	<tbody>" +
                "		<tr>" +
                "			<td>Warul Kawa IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/QLD/QLD3_Warul_Kawa_IPA_Information_Sheet.pdf\" target=\"_blank\">Warul Kawa IPA Fact Sheet</a></td>" +
                "			<td>Fact sheet information</td>" +
                "			<td>n/a</td>" +
                "			<td><a href=\"http://www.tsra.gov.au/\" target=\"_blank\">Torres Strait Regional Authority</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Pulu IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/QLD/QLD2_Pulu_IPA_Management%20Plan%202009.pdf\" target=\"_blank\">Pulu Indigenous Protected Area Plan of Management</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2009</td>" +
                "			<td><a href=\"http://puluipa.org\" target=\"_blank\">Pulu Indigenous Protected Area and Mabuygiw Rangers</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Warraberalgal and Porumalgal IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/TSI/TSI3_Torres-Strait-IPAs-Flyer.pdf\" target=\"_blank\">Warraberalgal and Porumalgal Indigenous Protected Area</a></td>" +
                "			<td>IPA Brochure</td>" +
                "			<td>n/a</td>" +
                "			<td><a href=\"http://www.tsra.gov.au/\" target=\"_blank\">Torres Strait Regional Authority</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Warraberalgal and Porumalgal IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/TSI/TSI4_Warraberalgal-and-Porumalgal-IPA-Brochure.PDF\" target=\"_blank\">Warraberalgal and Porumalgal Indigenous Protected Area</a></td>" +
                "			<td>Torres Strait IPAs Fact Sheet</td>" +
                "			<td>n/a</td>" +
                "			<td><a href=\"http://www.tsra.gov.au/\" target=\"_blank\">Torres Strait Regional Authority</a></td>" +
                "		</tr>" +
                "	</tbody>" +
                "</table>" +
                "<p>&nbsp;</p>" +
                "<table align=\"center\" border=\"1\" cellpadding=\"5\" cellspacing=\"0\" id=\"New South Wales\">" +
                "	<caption>" +
                "	<h3>New South Wales (<a href=\"https://northwestatlas.org/node/1703#map\">view a map</a>)</h3>" +
                "	</caption>" +
                "	<thead>" +
                "		<tr>" +
                "			<th scope=\"col\">Indigenous Protected Area</th>" +
                "			<th scope=\"col\">Name of plan</th>" +
                "			<th scope=\"col\">Document type</th>" +
                "			<th scope=\"col\">Time span</th>" +
                "			<th scope=\"col\">Source agency</th>" +
                "		</tr>" +
                "	</thead>" +
                "	<tbody>" +
                "		<tr>" +
                "			<td>Ngunya Jargoon IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/NSW/NSW1_ngunya-jargoon-ipa-plan-of-management.pdf\" target=\"_blank\">Ngunya Jargoon IPA Plan of Management</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2013</td>" +
                "			<td><a href=\"https://www.jalilalc.com/\" target=\"_blank\">Jali Local Aboriginal Land Council</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Minyumai IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/NSW/NSW2_Minyumai Plan of Management &amp; MERI Plan.pdf\" target=\"_blank\">Minyumai IPA Plan of Management &amp; MERI Plan</a></td>" +
                "			<td>Indigenous Management Plan</td>" +
                "			<td>2011</td>" +
                "			<td>Minyumai Land Holding Aboriginal Corporation</td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Gumma IPA</td>" +
                "			<td><a href=\"https://www.environment.gov.au/indigenous/ipa/declared/gumma.html\" target=\"_blank\">Gumma Indigenous Protected Area</a></td>" +
                "			<td>Fact Sheet Information</td>" +
                "			<td>n/a</td>" +
                "			<td>Nambucca Heads Local Aboriginal Land Council</td>" +
                "		</tr>" +
                "	</tbody>" +
                "</table>" +
                "<p>&nbsp;</p>" +
                "<table align=\"center\" border=\"1\" cellpadding=\"5\" cellspacing=\"0\" id=\"Victoria\">" +
                "	<caption>" +
                "	<h3>Victoria (<a href=\"https://northwestatlas.org/node/1703#map\">view a map</a>)</h3>" +
                "	</caption>" +
                "	<thead>" +
                "		<tr>" +
                "			<th scope=\"col\">Indigenous Protected Area</th>" +
                "			<th scope=\"col\">Name of plan</th>" +
                "			<th scope=\"col\">Document type</th>" +
                "			<th scope=\"col\">Time span</th>" +
                "			<th scope=\"col\">Source agency</th>" +
                "		</tr>" +
                "	</thead>" +
                "	<tbody>" +
                "		<tr>" +
                "			<td>" +
                "			<p>Deen Maar IPA</p>" +
                "			</td>" +
                "			<td><a href=\"https://www.environment.gov.au/indigenous/ipa/declared/deen-maar.html\" target=\"_blank\">Deen Maar Indigenous Protected Area</a></td>" +
                "			<td>Fact Sheet Information</td>" +
                "			<td>n/a</td>" +
                "			<td>Framlingham Aboriginal Trust</td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Lake Condah IPA, Kurtonitj IPA &amp; Tyrendarra IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/VIC/VIC1_NGNM-South-West-Management-Plan.pdf\" target=\"_blank\">Ngootyoong Gunditj Ngootyoong Mara South West Management Plan</a></td>" +
                "			<td>State Management Plan</td>" +
                "			<td>2015</td>" +
                "			<td><a href=\"https://www.gunditjmirring.com/indigenousprotectedareas\" target=\"_blank\">Gunditj Mirring Traditional Owners Aboriginal Corporation</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Tyrendarra IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/VIC/VIC2_fact sheet-tyrendarra.pdf\" target=\"_blank\">Tyrendarra Indigenous Protected Area</a></td>" +
                "			<td>Fact Sheet Information</td>" +
                "			<td>n/a</td>" +
                "			<td><a href=\"https://www.gunditjmirring.com/indigenousprotectedareas\" target=\"_blank\">Gunditj Mirring Traditional Owners Aboriginal Corporation</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Lake Condah IPA</td>" +
                "			<td><a href=\"https://www.environment.gov.au/indigenous/ipa/declared/lake-condah.html\" target=\"_blank\">Lake Condah Indigenous Protected Area</a></td>" +
                "			<td>Fact Sheet Information</td>" +
                "			<td>n/a</td>" +
                "			<td><a href=\"https://www.gunditjmirring.com/indigenousprotectedareas\" target=\"_blank\">Gunditj Mirring Traditional Owners Aboriginal Corporation</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Kurtonitj IPA</td>" +
                "			<td><a href=\"https://www.environment.gov.au/indigenous/ipa/declared/kurtonitj.html\" target=\"_blank\">Kurtonitj Indigenous Protected Area</a></td>" +
                "			<td>Fact Sheet Information</td>" +
                "			<td>n/a</td>" +
                "			<td><a href=\"https://www.gunditjmirring.com/indigenousprotectedareas\" target=\"_blank\">Gunditj Mirring Traditional Owners Aboriginal Corporation</a></td>" +
                "		</tr>" +
                "	</tbody>" +
                "</table>" +
                "<p>&nbsp;</p>" +
                "<table align=\"center\" border=\"1\" cellpadding=\"5\" cellspacing=\"0\" id=\"Tasmania\">" +
                "	<caption>" +
                "	<h3>Tasmania (<a href=\"https://northwestatlas.org/node/1703#map\">view a map</a>)</h3>" +
                "	</caption>" +
                "	<thead>" +
                "		<tr>" +
                "			<th scope=\"col\">Indigenous Protected Area</th>" +
                "			<th scope=\"col\">Name of plan</th>" +
                "			<th scope=\"col\">Document type</th>" +
                "			<th scope=\"col\">Time span</th>" +
                "			<th scope=\"col\">Source agency</th>" +
                "		</tr>" +
                "	</thead>" +
                "	<tbody>" +
                "		<tr>" +
                "			<td>Risdon Cove and Putalina IPA</td>" +
                "			<td><a href=\"https://www.environment.gov.au/indigenous/ipa/declared/oyster-risdon.html\" target=\"_blank\">Risdon Cove and Putalina Indigenous Protected Areas</a></td>" +
                "			<td>Fact Sheet Information</td>" +
                "			<td>n/a</td>" +
                "			<td><a href=\"http://tacinc.com.au/\" target=\"_blank\">Tasmanian Aboriginal Centre</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Preminghana IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/TAS/TAS1_Preminghana-Healthy-Country-Plan-_-Final.pdf\" target=\"_blank\">Preminghana Healthy Country Plan 2015</a></td>" +
                "			<td>Indigenous Healthy Country Plan</td>" +
                "			<td>2015</td>" +
                "			<td><a href=\"http://tacinc.com.au/\" target=\"_blank\">Tasmanian Aboriginal Centre</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>lungatalanana IPA, Babel Island IPA &amp; Big Dog Island IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/TAS/TAS2_lungtalanana Babel and Big Dog Healthy Country Plan.pdf\" target=\"_blank\">lungtalanana, Babel Island &amp; Big Dog Island Healthy Country Plan 2015</a></td>" +
                "			<td>Indigenous Healthy Country Plan</td>" +
                "			<td>2015</td>" +
                "			<td><a href=\"http://tacinc.com.au/\" target=\"_blank\">Tasmanian Aboriginal Centre</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Mount Chappell Island IPA &amp; Badger Island IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/TAS/TAS3_fact sheet-chappell-badger.pdf\" target=\"_blank\">Mount Chappell Island and Badger Island Indigenous Protected Areas</a></td>" +
                "			<td>Fact Sheet Information</td>" +
                "			<td>n/a</td>" +
                "			<td><a href=\"http://tacinc.com.au/\" target=\"_blank\">Tasmanian Aboriginal Centre</a></td>" +
                "		</tr>" +
                "	</tbody>" +
                "</table>" +
                "<p>&nbsp;</p>" +
                "<table align=\"center\" border=\"1\" cellpadding=\"5\" cellspacing=\"0\" id=\"South Australia\">" +
                "	<caption>" +
                "	<h3>South Australia (<a href=\"https://northwestatlas.org/node/1703#map\">view a map</a>)</h3>" +
                "	</caption>" +
                "	<thead>" +
                "		<tr>" +
                "			<th scope=\"col\">Indigenous Protected Area</th>" +
                "			<th scope=\"col\">Name of plan</th>" +
                "			<th scope=\"col\">Document type</th>" +
                "			<th scope=\"col\">Time span</th>" +
                "			<th scope=\"col\">Source agency</th>" +
                "		</tr>" +
                "	</thead>" +
                "	<tbody>" +
                "		<tr>" +
                "			<td>Wardang Island IPA</td>" +
                "			<td><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/SA/SA1_Sth_Yorke_Peninsula_CAP_Summary_June_2015.pdf\" target=\"_blank\">Conservation Action Planning June 2015 Summary Southern Yorke Peninsula</a></td>" +
                "			<td>Government Report</td>" +
                "			<td>2015</td>" +
                "			<td><a href=\"https://alt.sa.gov.au/wp/\" target=\"_blank\">South Australian Aboriginal Lands Trust</a></td>" +
                "		</tr>" +
                "		<tr>" +
                "			<td>Yalata IPA</td>" +
                "			<td>" +
                "			<p><a href=\"https://maps.northwestatlas.org/files/montara/links_to_plans/SA/SA2_Yalata IPA MERI Plan Draft.pdf\" target=\"_blank\">Yalata Indigenous Protected Area Draft MERI Plan – Monitoring, Evaluation, Reporting, Improvement (2011-2016)</a></p>" +
                "			</td>" +
                "			<td>Indigenous Healthy Country Draft Plan</td>" +
                "			<td>2011-2016</td>" +
                "			<td><a href=\"http://www.yalata.org/\" target=\"_blank\">Yalata Land Management , Yalata Community Inc.</a></td>" +
                "		</tr>" +
                "	</tbody>" +
                "</table>" +
                "<p>&nbsp;</p>" +
                "<h3>Important...</h3>" +
                "<p>Please read, cite and use these documents responsibly by referring to any special requirements outlined. Note that this is a rapidly evolving area with many new plans to be finalised and published shortly (<a href=\"https://www.abc.net.au/news/2016-11-23/kimberley-marine-park-created-around-horizontal-falls/8050330\" target=\"_blank\">here is an example</a>). It is your responsibility to ensure that you are aware of the most up to date information.</p>");
        drupalNode.setThumbnail(new URL("https://cdn131.picsart.com/339459399004201.jpg?to=crop&r=256"));
        drupalNode.setLangcode("en");
        results.add(new SearchResult()
            .setEntity(drupalNode.toJSON())
            .setIndex("eatlas_article")
            .setScore(23)
        );

        ExternalLink googleLink = new ExternalLink(
            "https://google.com",
            "https://www.google.com/logos/doodles/2020/december-holidays-days-2-30-6753651837108830.3-law.gif",
            "Google search engine"
        );
        googleLink.setDocument("<p>Google Search, I'm Feeling Lucky.</p>");
        googleLink.setLangcode("en");
        results.add(new SearchResult()
            .setEntity(googleLink.toJSON())
            .setIndex("eatlas_extlink")
            .setScore(12)
        );

        for (int i=0; i<200; i++) {
            results.add(this.getRandomGoogleSearchResult(i+1));
        }

        for (int i=0; i<50; i++) {
            results.add(this.getRandomLayerSearchResult(i+1));
        }

        for (int i=0; i<25; i++) {
            results.add(this.getRandomMetadataSearchResult(i+1));
        }

        return results;
    }

    private SearchResult getRandomGoogleSearchResult(int index) {
        Random random = new Random(91 + index);
        String randomSearchTerm = this.getRandomWord(random);

        ExternalLink googleSearchLink = new ExternalLink(
            String.format("https://www.google.com/search?q=%s", randomSearchTerm),
            "https://www.google.com/logos/doodles/2020/december-holidays-days-2-30-6753651837108830.3-law.gif",
            String.format("Google search %d for %s", index, randomSearchTerm)
        );
        googleSearchLink.setDocument(
                "<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.</p>" +
                String.format("<p>Google search result for random word %s.</p>", randomSearchTerm) +
                "<p>Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur?</p>");
        googleSearchLink.setLangcode("en");

        return new SearchResult()
            .setEntity(googleSearchLink.toJSON())
            .setIndex("eatlas_extlink")
            .setScore(12);
    }

    private SearchResult getRandomLayerSearchResult(int index) throws MalformedURLException {
        Random random = new Random(137 + index);
        String randomLayerName = "ea_" + this.getRandomWord(random);

        GeoServerLayer layerEntity = new GeoServerLayer(null);
        layerEntity.setLink(new URL(String.format("https://maps.eatlas.org.au/index.html?intro=false&z=7&ll=148.00000,-18.00000&l0=%s,ea_ea-be%%3AWorld_Bright-Earth-e-Atlas-basemap", randomLayerName)));
        layerEntity.setTitle(String.format("GeoServer random layer %d is %s", index, randomLayerName));
        layerEntity.setDocument(
                "<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.</p>" +
                String.format("<p>GeoServer random layer name %s.</p>", randomLayerName) +
                "<p>Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur?</p>");
        layerEntity.setThumbnail(new URL("https://github.com/aims-ks/atlasmapper/blob/master/logo/AtlasMapper_icon_256x242px.png?raw=true"));
        layerEntity.setLangcode("en");

        return new SearchResult()
            .setEntity(layerEntity.toJSON())
            .setIndex("eatlas_layer")
            .setScore(12);
    }

    private SearchResult getRandomMetadataSearchResult(int index) throws MalformedURLException {
        Random random = new Random(11 + index);
        String randomUUIDName = this.getRandomWord(random);
        String uuid = UUID.nameUUIDFromBytes(randomUUIDName.getBytes(StandardCharsets.UTF_8)).toString();

        GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(
                uuid,
                String.format("https://eatlas.org.au/data/faces/view.xhtml?uuid=%s", uuid),
                null);

        geoNetworkRecord.setTitle(String.format("Random metadata record %s", randomUUIDName));
        geoNetworkRecord.setDocument(
                "<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.</p>" +
                "<p>Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur?</p>");
        geoNetworkRecord.setThumbnail(new URL("https://eatlas.org.au/geonetwork/srv/en/resources.get?uuid=e8854605-d169-44ca-9364-aa5c2c87ff67&access=public&fname=preview-map_s.png"));
        geoNetworkRecord.setLangcode("en");

        return new SearchResult()
            .setEntity(geoNetworkRecord.toJSON())
            .setIndex("eatlas_metadata")
            .setScore(12);
    }

    private String getRandomWord(Random random) {
        // Word of length 3 through 10.
        char[] word = new char[random.nextInt(8)+3];
        for(int i = 0; i < word.length; i++) {
            word[i] = (char)('a' + random.nextInt(26));
        }
        return new String(word);
    }
}
