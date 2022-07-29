/*
 *  Copyright (C) 2022 Australian Institute of Marine Science
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
package au.gov.aims.eatlas.searchengine.entity;

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

public class GeoNetworkRecordTest {

    /*
     * GeoNetwork 2
     */

    @Test
    public void testParseWKT_65f61d2d_fe4e_48e5_8c6e_fab08450ef75() throws Exception {
        String index = "gn-test";
        String metadataRecordUUID = "65f61d2d-fe4e-48e5-8c6e-fab08450ef75";
        String metadataSchema = "iso19139.mcp";
        String geoNetworkUrl = "https://eatlas.org.au/geonetwork";

        Messages messages = Messages.getInstance(null);

        String expectedWKT = "POLYGON ((142.119140625 -9.931640625, 144.228515625 -9.84375, 144.4921875 -12.832031250000002, 145.810546875 -13.798828125, 147.12890625 -17.490234375, 153.45703125 -20.830078125, 153.80859375 -24.521484375, 151.083984375 -24.521484375, 148.447265625 -21.005859375000004, 146.337890625 -19.599609375, 144.755859375 -14.94140625, 143.61328125000003 -14.765625, 142.3828125 -11.77734375, 142.119140625 -9.931640625))";

        try (
            InputStream recordInputStream = GeoNetworkRecordTest.class.getClassLoader()
                    .getResourceAsStream("geonetworkRecords/geonetwork2/iso19139-mcp_65f61d2d-fe4e-48e5-8c6e-fab08450ef75.xml")
        ) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

            Document document = builder.parse(recordInputStream);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(index);
            geoNetworkRecord.parseRecord(metadataRecordUUID, metadataSchema, geoNetworkUrl, document, messages);

            Assert.assertEquals("Wrong WKT", expectedWKT, geoNetworkRecord.getWkt());
        }
    }

    @Test
    public void testParseWKT_87263960_92f0_4836_b8c5_8486660ddfe0() throws Exception {
        String index = "gn-test";
        String metadataRecordUUID = "87263960-92f0-4836-b8c5-8486660ddfe0";
        String metadataSchema = "iso19139.mcp";
        String geoNetworkUrl = "https://eatlas.org.au/geonetwork";

        Messages messages = Messages.getInstance(null);

        String expectedWKT = "POLYGON ((146.5157 -24.4984, 146.5157 -13.7908, 156.4019 -13.7908, 156.4019 -24.4984, 146.5157 -24.4984))";

        try (
            InputStream recordInputStream = GeoNetworkRecordTest.class.getClassLoader()
                    .getResourceAsStream("geonetworkRecords/geonetwork2/iso19139-mcp_87263960-92f0-4836-b8c5-8486660ddfe0.xml")
        ) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

            Document document = builder.parse(recordInputStream);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(index);
            geoNetworkRecord.parseRecord(metadataRecordUUID, metadataSchema, geoNetworkUrl, document, messages);

            Assert.assertEquals("Wrong WKT", expectedWKT, geoNetworkRecord.getWkt());
        }
    }

    @Test
    public void testParseWKT_ce58a4c2_c993_434a_aa57_62cfa919a2ab() throws Exception {
        String index = "gn-test";
        String metadataRecordUUID = "ce58a4c2-c993-434a-aa57-62cfa919a2ab";
        String metadataSchema = "iso19139.mcp";
        String geoNetworkUrl = "https://eatlas.org.au/geonetwork";

        Messages messages = Messages.getInstance(null);

        String expectedWKT = "POLYGON ((142.385 -24.167, 142.385 -10.412, 153.328 -10.412, 153.328 -24.167, 142.385 -24.167))";

        try (
            InputStream recordInputStream = GeoNetworkRecordTest.class.getClassLoader()
                    .getResourceAsStream("geonetworkRecords/geonetwork2/iso19139-mcp_ce58a4c2-c993-434a-aa57-62cfa919a2ab.xml")
        ) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

            Document document = builder.parse(recordInputStream);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(index);
            geoNetworkRecord.parseRecord(metadataRecordUUID, metadataSchema, geoNetworkUrl, document, messages);

            Assert.assertEquals("Wrong WKT", expectedWKT, geoNetworkRecord.getWkt());
        }
    }

    @Test
    public void testParseWKT_96C8FB9D_C3C4_11DE_3FCD_EA228444A7C1() throws Exception {
        String index = "gn-test";
        String metadataRecordUUID = "{96C8FB9D-C3C4-11DE-3FCD-EA228444A7C1}";
        String metadataSchema = "iso19139";
        String geoNetworkUrl = "https://eatlas.org.au/geonetwork";

        Messages messages = Messages.getInstance(null);

        String expectedWKT = "POLYGON ((151.129 -24.002, 151.129 -23.76, 151.411 -23.76, 151.411 -24.002, 151.129 -24.002))";

        try (
            InputStream recordInputStream = GeoNetworkRecordTest.class.getClassLoader()
                    .getResourceAsStream("geonetworkRecords/geonetwork2/iso19139_96C8FB9D-C3C4-11DE-3FCD-EA228444A7C1.xml")
        ) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

            Document document = builder.parse(recordInputStream);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(index);
            geoNetworkRecord.parseRecord(metadataRecordUUID, metadataSchema, geoNetworkUrl, document, messages);

            Assert.assertEquals("Wrong WKT", expectedWKT, geoNetworkRecord.getWkt());
        }
    }

    /*
     * GeoNetwork 3
     */

    @Test
    public void testParseWKT_8cfc0117_678f_4904_b11c_30be6e71ca80() throws Exception {
        String index = "gn-test";
        String metadataRecordUUID = "8cfc0117-678f-4904-b11c-30be6e71ca80";
        String metadataSchema = "iso19115-3.2018";
        String geoNetworkUrl = "https://eatlas.org.au/geonetwork";

        Messages messages = Messages.getInstance(null);

        String expectedWKT = "MULTIPOLYGON (((164.138460924232 -28.890553467287802, 165.061312486732 -26.478132044632595, 168.928499986732 -25.926121103473207, 170.598421861732 -26.478132044632595, 171.74099998673196 -28.273119780279693, 171.301546861732 -30.909470252182096, 168.137484361732 -32.6279823916197, 164.797640611732 -31.473356223177213, 164.138460924232 -28.890553467287802)), ((109.190340807045 -25.334281810757304, 110.569124986732 -19.857336624747205, 114.72745018204499 -16.562688834451592, 118.347445299232 -13.3337019587606, 123.252840807045 -11.534052879834306, 129.333773424232 -9.717380582625935, 133.464632799232 -8.893414805390606, 135.381747057045 -9.113146801745216, 140.320101549232 -10.625717949522098, 142.061434557045 -9.286666824257864, 144.610262682045 -9.460100960246038, 146.719637682045 -12.30800245344399, 151.026278307045 -14.019554430356195, 157.32693748673196 -14.059522494123996, 158.67276268204503 -15.633814236359498, 157.09073143204503 -18.656848547896104, 157.09073143204503 -20.3963152554643, 158.321200182045 -24.2972271512201, 159.787874986732 -25.965637066816896, 162.512484361732 -27.3792661801181, 163.083773424232 -30.152429407470095, 163.083773424232 -32.886678752251505, 160.403109361732 -35.108842523583, 156.18435936173196 -34.4591815606572, 155.157137682045 -35.54133294510241, 153.020296861732 -40.1181466819116, 153.13565330704498 -42.04128612503661, 151.99307518204498 -45.3445680638606, 149.065218736732 -47.01598342624889, 144.873934557045 -46.9878869782666, 142.237215807045 -45.8365967530215, 140.303622057045 -41.910605904740294, 138.194247057045 -40.7224379051572, 133.904085924232 -38.726393013141, 131.662874986732 -37.44667866215649, 130.388460924232 -35.43174890573939, 127.488070299232 -36.03816056606551, 125.202914049232 -37.4117814859882, 122.022372057045 -37.9356946073266, 115.166903307045 -38.3504331680361, 110.964632799232 -35.5748491964089, 111.47549705704502 -32.33373201464669, 110.157137682045 -29.6977744233714, 109.190340807045 -25.334281810757304), (114.01883201798199 -24.584779021778402, 114.469271471107 -25.787710955691807, 114.62308006485802 -26.438789670020505, 114.16165428360702 -26.880613356367405, 114.50223045548199 -27.933934976957104, 115.073519517982 -28.9578647350286, 115.31521873673199 -30.180924664139283, 115.79861717423199 -31.3139299024419, 116.534701158607 -32.0711117171235, 117.116976549232 -33.0986161195277, 119.072542955482 -33.61250254144579, 121.247835924232 -33.42932009691479, 122.97268943985702 -33.365114659343, 124.060335924232 -32.8220751414258, 125.752230455482 -32.2292395617814, 126.37845115860702 -32.052490418112, 128.432894517982 -31.800732407899098, 129.410677721107 -31.454614241304697, 131.212435533607 -31.304543457631112, 132.794466783607 -31.716660082179708, 134.123812486732 -32.0804209449381, 135.354281236732 -33.4109805269413, 135.760775377357 -33.986810970251405, 137.232943346107 -32.978887967518006, 137.628451158607 -31.782056269188303, 138.001986314857 -31.884728312929198, 138.727083971107 -33.9139037935842, 140.880404283607 -33.8956672427574, 140.836458971107 -34.5225673363354, 140.188265611732 -36.4369163951616, 141.188021471107 -37.8294725914858, 142.627230455482 -38.2448125598751, 143.539095689857 -38.45159820024178, 144.582796861732 -37.7426472071064, 145.198031236732 -37.6991962718184, 145.846224596107 -38.2102909064402, 146.813021471107 -38.39134671857191, 147.373324205482 -37.8034356875888, 148.548861314857 -37.5861046093904, 149.614535142982 -37.4379558932574, 149.889193346107 -36.029276241744, 150.548373033607 -34.802695852039, 150.845003892982 -33.72222560932061, 151.559115221107 -32.757424512230294, 152.273226549232 -32.12695286113329, 152.811556627357 -30.6640796219217, 152.866488267982 -29.7812434183181, 153.196078111732 -28.54369293238691, 152.85550193985702 -27.5839450362973, 152.78958397110696 -26.093972243229018, 152.18533592423202 -25.1230913315693, 151.581087877357 -24.494833345862702, 150.910921861732 -23.903603201060605, 150.427523424232 -23.772965167874602, 150.207796861732 -23.057177585914303, 149.328890611732 -22.631946682263504, 149.010287096107 -21.481375727803908, 148.395052721107 -20.6304055074923, 148.032503892982 -20.3525445259136, 147.208529283607 -20.270118922597405, 146.439486314857 -19.4331174018606, 145.978060533607 -18.935060231585013, 145.802279283607 -18.174753862036297, 145.769320299232 -17.557823174830204, 145.285921861732 -16.707428490879693, 145.110140611732 -15.6734849189382, 145.011263658607 -15.143915824730598, 144.472933580482 -14.655538357404396, 144.132357408607 -15.006008628835005, 143.626986314857 -14.772424777557703, 143.286410142982 -13.696892336485504, 143.231478502357 -12.991376275674412, 142.781039049232 -12.112037606743087, 142.605257799232 -11.315988235094608, 142.396517564857 -11.326760785643401, 142.165804674232 -12.44482282616309, 141.946078111732 -13.728911849699003, 142.143832017982 -15.239337490410293, 141.726351549232 -16.791590244225304, 141.407748033607 -18.091228185729392, 140.979281236732 -18.612604481148708, 139.803744127357 -18.64383678320729, 138.869906236732 -17.91360418366739, 138.705111314857 -17.327237934574, 137.760287096107 -16.90725173662291, 136.562777330482 -16.391492640537507, 135.958529283607 -15.789807737191282, 135.134554674232 -15.345310798795495, 134.629183580482 -14.8149133725164, 135.343294908607 -14.059522494123996, 135.738802721107 -13.012785594498908, 135.552035142982 -12.659297553802105, 134.837923814857 -12.927137255684997, 133.871126939857 -12.509183993348202, 133.387728502357 -12.197957853583404, 132.519808580482 -13.216081523190695, 131.487093736732 -12.937844909401207, 130.871859361732 -13.044896105657585, 130.641146471107 -13.867612443087694, 130.454378892982 -15.47240777536328, 129.498568346107 -16.728472415970316, 128.476839830482 -16.812624862705988, 127.773714830482 -16.011692865878913, 127.488070299232 -15.493583037701697, 127.58694725235702 -14.751177362393506, 127.03763084610699 -14.368371734825985, 125.90603904923199 -15.0378412959803, 125.18094139298199 -15.9483221151022, 125.312777330482 -16.47579275100439, 124.554720689857 -16.823141297195832, 124.10428123673202 -17.557823174830204, 123.47806053360699 -17.997214238201195, 122.280550767982 -18.654246274750392, 121.61038475235699 -19.526334580745, 120.050326158607 -20.280424522226397, 118.325472642982 -20.640686964580382, 117.523470689857 -21.112878192934005, 116.51272850235699 -21.225572593495286, 115.39212303360699 -21.981444452141986, 114.72195701798202 -22.713045394644297, 114.205599596107 -23.53144261606451, 114.01883201798199 -24.584779021778402), (144.92886619767 -41.2180738848275, 145.642977525795 -42.0881838226326, 145.796786119545 -42.5269231481506, 145.63199119767 -42.7370769204489, 146.170321275795 -43.18730447767, 146.70865135392 -43.3313205522309, 146.97232322892 -42.9143449530853, 147.554598619545 -42.66441168871761, 147.80728416642 -42.4215802172387, 147.972079088295 -41.736631805548, 148.147860338295 -40.96968438988691, 147.290926744545 -41.1602003485326, 147.093172838295 -41.440821003215994, 146.37906151017 -41.391387463621705, 145.56607322892 -41.2015438088208, 144.97281151017 -40.936494848939, 144.92886619767 -41.2180738848275)), ((102.087679674232 -10.92790931688458, 102.615023424232 -8.806570569230487, 105.99881248673199 -9.717380582625935, 109.206820299232 -11.186686118066902, 107.976351549232 -13.248165469877407, 105.42752342423199 -14.059522494123996, 103.01053123673202 -13.034193064686605, 102.087679674232 -10.92790931688458)), ((93.16678123673239 -11.574416739732982, 94.7488124867324 -8.980238449017463, 97.2097499867324 -8.502456389246419, 99.1433437367324 -9.197206190300292, 100.110140611732 -10.92790931688458, 100.417757799232 -12.734321287453099, 99.31912498673242 -14.698049753046703, 96.7263515492324 -15.758089932802804, 94.00174217423242 -14.485410490028698, 93.16678123673239 -11.574416739732982)))";

        try (
            InputStream recordInputStream = GeoNetworkRecordTest.class.getClassLoader()
                    .getResourceAsStream("geonetworkRecords/geonetwork3/iso19115-3-2018_8cfc0117-678f-4904-b11c-30be6e71ca80.xml")
        ) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

            Document document = builder.parse(recordInputStream);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(index);
            geoNetworkRecord.parseRecord(metadataRecordUUID, metadataSchema, geoNetworkUrl, document, messages);

            Assert.assertEquals("Wrong WKT", expectedWKT, geoNetworkRecord.getWkt());
        }
    }

    @Test
    public void testParseWKT_09ac8e36_5d65_40f9_9bb7_c32a0dd9f24f() throws Exception {
        String index = "gn-test";
        String metadataRecordUUID = "09ac8e36-5d65-40f9-9bb7-c32a0dd9f24f";
        String metadataSchema = "iso19115-3.2018";
        String geoNetworkUrl = "https://eatlas.org.au/geonetwork";

        Messages messages = Messages.getInstance(null);

        String expectedWKT = "POLYGON ((108.84748496956072 -22.30507429122079, 119.8628170403583 -10.684919738574393, 122.31804165854811 -11.271204821048414, 124.242406899832 -10.42398102235289, 127.49392058338067 -8.591578798146685, 129.75007293523078 -8.591578798146685, 130.3472897342499 -10.880477041265351, 129.81643035734402 -11.726382415605357, 129.9491452015705 -12.763930535633023, 128.7547116035322 -14.18357317398548, 127.36120573915422 -13.53932744032555, 125.63591276421 -13.216543831150489, 123.31340299024667 -15.274560757952344, 122.11896939220837 -16.61442055100747, 121.78718228164219 -17.755507307509546, 121.05725063839657 -19.202986728882806, 119.59738735190531 -19.32826938582585, 115.15144007031836 -20.699927790973646, 112.82893029635501 -22.30507429122079, 108.84748496956072 -22.30507429122079))";

        try (
            InputStream recordInputStream = GeoNetworkRecordTest.class.getClassLoader()
                    .getResourceAsStream("geonetworkRecords/geonetwork3/iso19115-3-2018_09ac8e36-5d65-40f9-9bb7-c32a0dd9f24f.xml")
        ) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

            Document document = builder.parse(recordInputStream);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(index);
            geoNetworkRecord.parseRecord(metadataRecordUUID, metadataSchema, geoNetworkUrl, document, messages);

            Assert.assertEquals("Wrong WKT", expectedWKT, geoNetworkRecord.getWkt());
        }
    }

    @Test
    public void testParseWKT_a2a8f9c0_d7bc_4fae_b9b1_ccebfa642068() throws Exception {
        String index = "gn-test";
        String metadataRecordUUID = "a2a8f9c0-d7bc-4fae-b9b1-ccebfa642068";
        String metadataSchema = "iso19115-3.2018";
        String geoNetworkUrl = "https://eatlas.org.au/geonetwork";

        Messages messages = Messages.getInstance(null);

        String expectedWKT = "MULTIPOLYGON (((147.05298900613 -19.271664626425, 147.05298900613 -19.270462996787, 147.05479145059 -19.270462996787, 147.05479145059 -19.271664626425, 147.05298900613 -19.271664626425)), ((146.5325 -18.766399999999997, 146.5325 -18.7653, 146.53359999999998 -18.7653, 146.53359999999998 -18.766399999999997, 146.5325 -18.766399999999997)))";

        try (
            InputStream recordInputStream = GeoNetworkRecordTest.class.getClassLoader()
                    .getResourceAsStream("geonetworkRecords/geonetwork3/iso19115-3-2018_a2a8f9c0-d7bc-4fae-b9b1-ccebfa642068.xml")
        ) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

            Document document = builder.parse(recordInputStream);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(index);
            geoNetworkRecord.parseRecord(metadataRecordUUID, metadataSchema, geoNetworkUrl, document, messages);

            Assert.assertEquals("Wrong WKT", expectedWKT, geoNetworkRecord.getWkt());
        }
    }

    @Test
    public void testParseWKT_0fd70612_a07a_492a_bacf_8e0b7951da4d() throws Exception {
        String index = "gn-test";
        String metadataRecordUUID = "0fd70612-a07a-492a-bacf-8e0b7951da4d";
        String metadataSchema = "iso19115-3.2018";
        String geoNetworkUrl = "https://eatlas.org.au/geonetwork";

        Messages messages = Messages.getInstance(null);

        String expectedWKT = "MULTIPOLYGON (((147.05549000000002 -19.27279, 147.05549000000002 -19.271790000000003, 147.05649 -19.271790000000003, 147.05649 -19.27279, 147.05549000000002 -19.27279)), ((146.8433 -19.181829999999998, 146.8433 -19.18083, 146.84429999999998 -19.18083, 146.84429999999998 -19.181829999999998, 146.8433 -19.181829999999998)), ((145.9693 -16.756670000000003, 145.9693 -16.755670000000006, 145.97029999999998 -16.755670000000006, 145.97029999999998 -16.756670000000003, 145.9693 -16.756670000000003)))";

        try (
            InputStream recordInputStream = GeoNetworkRecordTest.class.getClassLoader()
                    .getResourceAsStream("geonetworkRecords/geonetwork3/iso19115-3-2018_0fd70612-a07a-492a-bacf-8e0b7951da4d.xml")
        ) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

            Document document = builder.parse(recordInputStream);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(index);
            geoNetworkRecord.parseRecord(metadataRecordUUID, metadataSchema, geoNetworkUrl, document, messages);

            Assert.assertEquals("Wrong WKT", expectedWKT, geoNetworkRecord.getWkt());
        }
    }

    @Test
    public void testParseWKT_74f6e33f_9347_4446_9185_488bcd7be964() throws Exception {
        String index = "gn-test";
        String metadataRecordUUID = "74f6e33f-9347-4446-9185-488bcd7be964";
        String metadataSchema = "iso19115-3.2018";
        String geoNetworkUrl = "https://eatlas.org.au/geonetwork";

        Messages messages = Messages.getInstance(null);

        String expectedWKT = "POLYGON ((148.707 -24.498, 148.707 -19.659, 154.001 -19.659, 154.001 -24.498, 148.707 -24.498))";

        try (
            InputStream recordInputStream = GeoNetworkRecordTest.class.getClassLoader()
                    .getResourceAsStream("geonetworkRecords/geonetwork3/iso19115-3-2018_74f6e33f-9347-4446-9185-488bcd7be964.xml")
        ) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

            Document document = builder.parse(recordInputStream);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(index);
            geoNetworkRecord.parseRecord(metadataRecordUUID, metadataSchema, geoNetworkUrl, document, messages);

            Assert.assertEquals("Wrong WKT", expectedWKT, geoNetworkRecord.getWkt());
        }
    }

    @Test
    public void testParseWKT_053f0b32_47cc_4a3f_8325_b37feb33c0e3() throws Exception {
        String index = "gn-test";
        String metadataRecordUUID = "053f0b32-47cc-4a3f-8325-b37feb33c0e3";
        String metadataSchema = "iso19115-3.2018";
        String geoNetworkUrl = "https://eatlas.org.au/geonetwork";

        Messages messages = Messages.getInstance(null);

        String expectedWKT = "POLYGON ((142.361095375504 -11.01950630219134, 142.5594177922386 -10.68272914450409, 142.7596102859341 -10.68297245877473, 143.163870421923 -11.14763393908733, 143.1864348315673 -11.52539312521933, 143.5443099917767 -11.80715043369878, 143.4817500541533 -12.23790732922733, 143.74617079876 -13.33011644035989, 144.2871781410731 -13.99952422730477, 145.6328149394436 -14.75642612392292, 145.3029259479546 -15.42610850933827, 145.3881523304479 -15.65013459790775, 145.6297526249028 -15.69506240802916, 145.4114433208368 -15.79454501341779, 145.6255792472889 -16.37949168463397, 145.480021176796 -16.42130919303264, 145.7890747513625 -16.80550880314402, 146.0538350712492 -16.89601256442663, 145.9425706559311 -17.03638896212518, 146.1443529276549 -17.20388529600304, 146.0430048590254 -17.24662980821674, 146.4059900700244 -18.38880981752639, 146.9216287497446 -19.068216479707, 147.4443750131235 -19.26446514529517, 147.9562693650281 -19.85538406782607, 148.9914430489278 -20.01156660508909, 149.234601954185 -20.23026659850956, 149.2282280562002 -20.55923313166188, 149.9455868793219 -20.96518667132178, 150.7509893405538 -21.9259991372333, 150.7734219056489 -22.3194985526817, 151.046129721659 -22.70666975766173, 151.3917168541562 -23.75197787759969, 151.8163496659659 -23.938491366753, 152.1874037967991 -24.5869714617134, 152.4280912446703 -24.69666613879398, 153.316309995841 -24.63905587503815, 153.4138376593349 -25.00885246882392, 153.1322610260599 -25.67733746075679, 153.2451585410425 -25.94191651085546, 152.8678067573677 -26.03502448115904, 152.9772714798252 -26.15921692727923, 152.828150808959 -26.24908436629878, 152.9962704693747 -26.41274548498072, 152.8751692334905 -26.46729500599707, 152.8886606906699 -26.77501267401718, 152.501669262346 -26.75463375064727, 152.2821704226122 -26.44679107717941, 151.7219452399198 -26.94182841703884, 151.2099937397004 -26.50555427000666, 151.2275158083153 -26.32759954980076, 151.0458495833935 -26.17271144034115, 150.5996429522566 -26.27184529809052, 150.2014854637394 -26.12669014700059, 150.1981790435622 -26.301130666268, 149.9483535217835 -26.42005966469678, 148.9989778449891 -26.30171388505817, 148.6828552180638 -26.0217299105099, 148.2487189005399 -26.05951134399771, 148.384911192727 -25.27570806029831, 148.0563987777601 -24.90831840844686, 147.7366104921999 -24.72359521215558, 147.0000974750275 -25.03234391687795, 146.8816578851531 -24.59939556095606, 146.6701903628065 -24.6119325473408, 146.3540816013465 -24.25235320121381, 146.4407219453978 -23.91178855007068, 146.1359844735345 -23.23787306516748, 146.1085727834154 -22.24379446541291, 145.9931775043601 -22.06341436830487, 145.655060569068 -21.96021902727681, 145.7574905605985 -21.81006150666137, 145.9708444137881 -21.8762573338363, 146.1179312271712 -21.57564059428946, 145.703309715917 -21.15844809946786, 145.602944723158 -21.43952194245053, 145.3434975809332 -21.30852923446185, 145.3503041235126 -20.94151582374935, 144.7765596660491 -19.91702272125728, 144.4308183224526 -19.97195835132454, 144.2303133409986 -19.67381901373288, 144.7682127263315 -19.13708737003084, 144.5675247007622 -18.85429306923892, 144.6623415471777 -18.44831236398871, 144.9918478122372 -17.60764223589277, 145.4368860293391 -17.29934757976991, 145.3197758592215 -17.00749817971015, 145.5121598297918 -16.64816790914728, 144.7335123772079 -15.88436880583049, 144.3635716930132 -15.94169890171674, 143.8474858638063 -15.80505140050894, 143.3044799557704 -15.18164296277707, 143.4864149856072 -14.58374441861359, 143.1870788691785 -14.03804792318572, 143.3502805902459 -13.75840416760929, 143.3491500040451 -13.162576343331, 142.9609385483084 -12.97711415299445, 142.9276295985792 -12.43469668495297, 142.7521779571392 -12.38261607703864, 142.613834126429 -11.91971955077695, 142.8104643054001 -11.34124095712693, 142.66110124028 -11.11319759903067, 142.361095375504 -11.01950630219134))";

        try (
            InputStream recordInputStream = GeoNetworkRecordTest.class.getClassLoader()
                    .getResourceAsStream("geonetworkRecords/geonetwork3/iso19115-3-2018_053f0b32-47cc-4a3f-8325-b37feb33c0e3.xml")
        ) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

            Document document = builder.parse(recordInputStream);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(index);
            geoNetworkRecord.parseRecord(metadataRecordUUID, metadataSchema, geoNetworkUrl, document, messages);

            Assert.assertEquals("Wrong WKT", expectedWKT, geoNetworkRecord.getWkt());
        }
    }

    @Test
    public void testParseWKT_a7828f28_402c_4ef7_beac_6c0f61852072() throws Exception {
        String index = "gn-test";
        String metadataRecordUUID = "a7828f28-402c-4ef7-beac-6c0f61852072";
        String metadataSchema = "iso19115-3.2018";
        String geoNetworkUrl = "https://eatlas.org.au/geonetwork";

        Messages messages = Messages.getInstance(null);

        String expectedWKT = "POLYGON ((146.15661621099 -16.898377127935, 146.1573028565 -16.837952323247, 146.2630385495819 -16.837952323247, 146.26235961919 -16.898377127935, 146.15661621099 -16.898377127935))";

        try (
            InputStream recordInputStream = GeoNetworkRecordTest.class.getClassLoader()
                    .getResourceAsStream("geonetworkRecords/geonetwork3/iso19115-3-2018_a7828f28-402c-4ef7-beac-6c0f61852072.xml")
        ) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

            Document document = builder.parse(recordInputStream);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(index);
            geoNetworkRecord.parseRecord(metadataRecordUUID, metadataSchema, geoNetworkUrl, document, messages);

            Assert.assertEquals("Wrong WKT", expectedWKT, geoNetworkRecord.getWkt());
        }
    }

    @Test
    public void testParseWKT_16904861_53e6_4123_a7bb_781f2429629f() throws Exception {
        // NOTE: The polygon for this metadata record is a mess.
        //   It looks like someone tried to draw multiple polygons using a single polygon.
        //   There is a lot of intersecting lines.
        //   The JTS library does quite a good job a generating a usable geometry from it.
        String index = "gn-test";
        String metadataRecordUUID = "16904861-53e6-4123-a7bb-781f2429629f";
        String metadataSchema = "iso19115-3.2018";
        String geoNetworkUrl = "https://eatlas.org.au/geonetwork";

        Messages messages = Messages.getInstance(null);

        String expectedWKT = "MULTIPOLYGON (((139.0869140625 -16.6552734375, 140.20449577121568 -14.453976132453958, 139.4384765625 -16.2158203125, 139.7900390625 -16.6552734375, 139.0869140625 -16.6552734375)), ((141.2561812889543 -12.382474355089998, 141.43792707607938 -12.02449022893454, 141.9873046875 -12.4365234375, 141.5478515625 -13.4033203125, 141.2561812889543 -12.382474355089998)), ((141.68580878053535 -11.536238386824326, 141.9873046875 -10.9423828125, 142.8662109375 -9.9755859375, 144.7998046875 -14.1064453125, 146.6455078125 -14.985351562500002, 146.99707031250003 -18.0615234375, 149.6337890625 -19.6435546875, 151.2158203125 -22.368164062500004, 153.7646484375 -24.8291015625, 153.9404296875 -28.0810546875, 153.5009765625 -28.0810546875, 152.8857421875 -25.3564453125, 150.9521484375 -23.7744140625, 150.6005859375 -22.8076171875, 149.5458984375 -22.4560546875, 149.1064453125 -21.2255859375, 149.0185546875 -20.3466796875, 146.2939453125 -19.0283203125, 145.8544921875 -17.0947265625, 145.2392578125 -16.3916015625, 145.2392578125 -15.161132812500002, 144.7119140625 -14.1943359375, 143.2177734375 -10.7666015625, 142.5146484375 -10.7666015625, 141.68580878053535 -11.536238386824326)))";

        try (
            InputStream recordInputStream = GeoNetworkRecordTest.class.getClassLoader()
                    .getResourceAsStream("geonetworkRecords/geonetwork3/iso19115-3-2018_16904861-53e6-4123-a7bb-781f2429629f.xml")
        ) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

            Document document = builder.parse(recordInputStream);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(index);
            geoNetworkRecord.parseRecord(metadataRecordUUID, metadataSchema, geoNetworkUrl, document, messages);

            Assert.assertEquals("Wrong WKT", expectedWKT, geoNetworkRecord.getWkt());
        }
    }

    @Test
    public void testParseWKT_b67d8331_6505_450b_bc64_ee4b57ee35a3() throws Exception {
        // NOTE: The polygon for this metadata record is a mess.
        //   It looks like someone tried to draw multiple polygons using a single polygon.
        //   There is a lot of intersecting lines.
        //   The JTS library does quite a good job a generating a usable geometry from it.
        String index = "gn-test";
        String metadataRecordUUID = "b67d8331-6505-450b-bc64-ee4b57ee35a3";
        String metadataSchema = "iso19115-3.2018";
        String geoNetworkUrl = "https://eatlas.org.au/geonetwork";

        Messages messages = Messages.getInstance(null);

        String expectedWKT = "POLYGON ((-175.4736328125 -31.245117187500004, -171.2548828125 36.9580078125, 171.8701171875 39.0673828125, 175.3857421875 -30.5419921875, -32.0361328125 -34.0576171875, -175.4736328125 -31.245117187500004))";

        try (
            InputStream recordInputStream = GeoNetworkRecordTest.class.getClassLoader()
                    .getResourceAsStream("geonetworkRecords/geonetwork3/iso19115-3-2018_b67d8331-6505-450b-bc64-ee4b57ee35a3.xml")
        ) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

            Document document = builder.parse(recordInputStream);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(index);
            geoNetworkRecord.parseRecord(metadataRecordUUID, metadataSchema, geoNetworkUrl, document, messages);

            Assert.assertEquals("Wrong WKT", expectedWKT, geoNetworkRecord.getWkt());
        }
    }

    @Test
    public void testParseWKT_ac57aa5a_233b_4c2c_bd52_1fb40a31f639() throws Exception {
        // NOTE: The polygon for this metadata record is a mess.
        //   It looks like someone tried to draw multiple polygons using a single polygon.
        //   There is a lot of intersecting lines.
        //   The JTS library does quite a good job a generating a usable geometry from it.
        String index = "gn-test";
        String metadataRecordUUID = "ac57aa5a-233b-4c2c-bd52-1fb40a31f639";
        String metadataSchema = "iso19115-3.2018";
        String geoNetworkUrl = "https://eatlas.org.au/geonetwork";

        Messages messages = Messages.getInstance(null);

        String expectedWKT = "POLYGON ((-180 89.9995, -180 90, 180 90, 180 89.9995, -180 89.9995))";

        try (
            InputStream recordInputStream = GeoNetworkRecordTest.class.getClassLoader()
                    .getResourceAsStream("geonetworkRecords/geonetwork3/iso19115-3-2018_ac57aa5a-233b-4c2c-bd52-1fb40a31f639.xml")
        ) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();

            Document document = builder.parse(recordInputStream);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(index);
            geoNetworkRecord.parseRecord(metadataRecordUUID, metadataSchema, geoNetworkUrl, document, messages);

            Assert.assertEquals("Wrong WKT", expectedWKT, geoNetworkRecord.getWkt());
        }
    }

}
