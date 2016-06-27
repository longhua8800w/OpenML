package org.openml.cortana;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.openml.apiconnector.algorithms.*;
import org.openml.apiconnector.io.*;
import org.openml.apiconnector.settings.*;
import org.openml.apiconnector.xml.*;
import org.openml.apiconnector.xml.DataFeature.Feature;
import org.openml.apiconnector.xml.Run.Parameter_setting;
import org.openml.apiconnector.xml.SetupParameters.Parameter;
import org.openml.apiconnector.xml.Task.Input;
import org.openml.apiconnector.xml.Task.Input.Data_set;
import org.openml.apiconnector.xstream.XstreamXmlMapping;
import org.openml.cortana.utils.SdFlow;
import org.openml.cortana.xml.AutoRun;
import org.openml.cortana.xml.AutoRun.Experiment.Table.*;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.*;

public class CLI {

	public static final String[] TAGS = {"Cortana"};
	
	private static final XStream xstream = new XStream(new DomDriver("UTF-8", new XmlFriendlyNameCoder("_-", "_")));
	
	public static void main(String[] args) throws Exception {
		xstream.processAnnotations(AutoRun.class);
		
		CommandLineParser parser = new GnuParser();
		Options options = new Options();
		options.addOption("config", true, "The config string describing the settings for API interaction");
		options.addOption("c", true, "The cortana jar location");
		options.addOption("t", true, "The task id");
		options.addOption("s", true, "The setup id (for setting search parameters)");
	//	options.addOption("xml", true, "The auto run xml (for setting search parameters)");
		options.addOption("json", true, "The auto run json (for setting search parameters)");
		
		CommandLine cli  = parser.parse(options, args);
		Config config;
		if(cli.hasOption("-config") == false) {
			config = new Config();
		} else {
			config = new Config(cli.getOptionValue("config"));
		}
		OpenmlConnector openml = new OpenmlConnector(config.getServer(),config.getApiKey());
		
		Integer task_id;
		String cortanaJar;
		if (cli.hasOption("-t") == false) {
			throw new Exception("Task parameter (-t) not set");
		} else {
			task_id = Integer.parseInt(cli.getOptionValue("t"));
		}
		if (cli.hasOption("-c") == false) {
			throw new Exception("Cortana jar location parameter (-c) not set");
		} else {
			cortanaJar = cli.getOptionValue("c");
		}
		
		Map<String, String> searchParams = new HashMap<String, String>();
		if (cli.hasOption("-s")) {
			SetupParameters sp = openml.setupParameters(Integer.parseInt(cli.getOptionValue("s")));
			
			for (Parameter p : sp.getParameters()) {
				searchParams.put(p.getParameter_name(), p.getValue());
			}
		/*} else if (cli.hasOption("-xml")) {
			File file = new File(cli.getOptionValue("xml"));
			if (file.exists() == false) {
				throw new Exception("Could not find search parameters file (specified by -xml)");
			}
			
			
			AutoRun ar = (AutoRun) xstream.fromXML(file);
			searchParams = ar.getExperiment().getSearchParameters().getParameters();
			*/
		} else if (cli.hasOption("-json")) {
			JSONObject jObject = new JSONObject(cli.getOptionValue("json"));
	        Iterator<?> keys = jObject.keys();

	        while( keys.hasNext() ){
	            String key = (String)keys.next();
	            String value = jObject.getString(key); 
	            searchParams.put(key, value);
	        }
		} else {
			throw new Exception("Search parameters not specified (-s or -json)");
		}
		
		process(openml, task_id, cortanaJar, searchParams);
		
	}
	
	private static void process(OpenmlConnector openml, Integer task_id, String cortanaJar, Map<String,String> searchParams) throws Exception {
		
		String current_run_name = "Cortana-Run-" + ManagementFactory.getRuntimeMXBean().getName();
		
		Task task = openml.taskGet(task_id);
		
		if (task.getTask_type().equals("Subgroup Discovery") == false) {
			throw new Exception("Wrong task type. ");
		}
		
		Integer dataset_id = null;
		String target_feature = null;
		String target_value = null;
		Double time_limit = null;
		String quality_measure = null;
		
		for (Input i : task.getInputs()) {
			if (i.getName().equals("source_data")) {
				Data_set ds = i.getData_set();
				
				dataset_id = ds.getData_set_id();
				target_feature = ds.getTarget_feature();
				target_value = ds.getTarget_value();
				
			}
			
			if (i.getName().equals("time_limit")) {
				time_limit = i.getTime_limit();
			}
			
			if (i.getName().equals("quality_measure")) {
				quality_measure = i.getQuality_measure();
			}
		}
		searchParams.put("time_limit", "" + time_limit);
		
		DataSetDescription dsd = openml.dataGet(dataset_id);
		File dataset = dsd.getDataset(openml.getApiKey());
		File datasetTmp = Conversion.stringToTempFile("", dsd.getName(), dsd.getFormat());
		IOUtils.copy(new FileInputStream(dataset), new FileOutputStream(datasetTmp));
		
		Feature[] features = openml.dataFeatures(dataset_id).getFeatures();
		Column[] column = new Column[features.length];
		for (int i = 0; i < features.length; ++i) {
			column[i] = new Column(features[i].getDataType(), features[i].getName(), i, "0.0", true);
		}
		
		AutoRun ar = new AutoRun(
			target_feature, target_value, quality_measure, 
			Integer.parseInt(searchParams.get("search_depth")), 
			Integer.parseInt(searchParams.get("minimum_coverage")), 
			Double.parseDouble(searchParams.get("maximum_coverage_fraction")), 
			Integer.parseInt(searchParams.get("maximum_subgroups")), 
			Double.parseDouble(searchParams.get("time_limit")), 
			searchParams.get("search_strategy"), 
			Boolean.parseBoolean(searchParams.get("use_nominal_sets")), 
			Integer.parseInt(searchParams.get("search_strategy_width")), 
			searchParams.get("numeric_operators"), 
			searchParams.get("numeric_strategy"), 
			Integer.parseInt(searchParams.get("nr_bins")), 
			Integer.parseInt(searchParams.get("nr_threads")), 
			dsd.getName(), datasetTmp.getName(), column);
		
		File runXMLtmp = Conversion.stringToTempFile("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<!DOCTYPE autorun SYSTEM \"autorun.dtd\">\n" + xstream.toXML(ar), current_run_name, "xml");
		String cmd = "java -jar " + cortanaJar + " " + runXMLtmp.getAbsolutePath() + " 0 1";
	//	String[] cliArguments = {runXMLtmp.getAbsolutePath(), "0", "1"};
		
		executeCommand(cmd);
		
		File dir = runXMLtmp.getParentFile();
		
		File resultTxt = null;
		for (File f : dir.listFiles()) {
			if (f.getName().startsWith(current_run_name) && f.getName().endsWith(".txt")) {
				
				if (resultTxt == null) {
					resultTxt = f;
				} else {
					throw new Exception("Multiple candidates for outputfile. ");
				}
			}
		}
		if (resultTxt == null) { throw new Exception("Result txt file not found. "); }
		File subgroups = File.createTempFile("subgroups", ".csv");
		resultTxt.renameTo(subgroups);

		// update search params with only relevant parameters
		searchParams = ar.getExperiment().getSearchParameters().getParameters();

		int flow_id = SdFlow.getFlowId(openml);
		Parameter_setting[] params = new Parameter_setting[searchParams.size()];
		
		int i = 0;
		for (String key : searchParams.keySet()) {
			params[i++] = new Parameter_setting(flow_id, key, searchParams.get(key));
		}
		
		Run r = new Run(task_id, null, flow_id, null, params, TAGS);
		File runfile = Conversion.stringToTempFile(XstreamXmlMapping.getInstance().toXML(r), "run", "xml");
		
		System.out.println(XstreamXmlMapping.getInstance().toXML(r));
		
		Map<String,File> uploadFiles = new HashMap<String, File>();
		uploadFiles.put("subgroups", subgroups);
		
		UploadRun ur = openml.runUpload(runfile, uploadFiles);
		
		System.out.println(ur.getRun_id());
	}
	
	private static boolean executeCommand(String cmd) {
		try {
			@SuppressWarnings("unused")
			String line;
			Process p = Runtime.getRuntime().exec(cmd);
			BufferedReader bri = new BufferedReader(new InputStreamReader(
					p.getInputStream()));
			BufferedReader bre = new BufferedReader(new InputStreamReader(
					p.getErrorStream()));
			while ((line = bri.readLine()) != null) {
				//System.out.println(line);
			}
			bri.close();
			while ((line = bre.readLine()) != null) {
				//System.out.println(line);
			}
			bre.close();
			p.waitFor();
			return true;
		} catch (Exception err) {
			err.printStackTrace();
			return false;
		}
	}
}