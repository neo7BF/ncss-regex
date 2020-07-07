package it.neo7bf.ncssregex;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NcssRegexApplication  implements CommandLineRunner {
	
	public static final String COUNT_COMMENTS="COUNT_COMMENTS";
	public static final String COUNT_NCSS="COUNT_NCSS";
	public static final String COUNT_ALL_LINES = "COUNT_ALL_LINES";
	
	//INFO: ${} it is only aplaceHolder #{} instead it is a evaluable expression
	
	//@Value("${ncss.regex.launch.mode:COUNT_NCSS}")
	//SPEL: the placeHolder it is replaced with the default value or with the value in the properties
	//then trim is called on that string
	
	@Value("#{'${ncss.regex.launch.mode:COUNT_NCSS}'.trim()}")
	private String ncssRegexlaunchMode;
	
	@Value("#{'${ncss.regex.target.path}'.trim()}")
	private String ncssRegexTargetPath;
	
	@Value("#{'${ncss.regex.find.depth:10}'.trim()}")
	private int ncssRegexFindDepth;
	//be careful when copying and pasting regexes. Editor can adds escapes that can change the behavior of the regex
	@Value("${ncss.regex:(/\\*([^*]|[\\r\\n]|(\\*+([^*/]|[\\r\\n])))*\\*+/)|(//.*)}")
	private String ncssRegex;

	@Value("#{'${ncss.regex.exclude:.metadata}'.split(',')}") 
	List<String> ncssRegexExclude;
	//Default Charset cp1252 for resolve java.nio.charset.MalformedInputException when reading lines of files in Windows systems.
	@Value("${ncss.regex.read.lines.charset:Cp1252}")
	String ncssRegexCharset;
	
    private static final Logger LOGGER = LoggerFactory.getLogger(NcssRegexApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(NcssRegexApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		long start = System.currentTimeMillis();
		
		if(ncssRegexTargetPath == null || ncssRegexTargetPath.isEmpty()) {
			throw new RuntimeException ("ncss.regex.target.path: The path of the folder to be analyzed must be provided");
		}
		
		File dir = new File(ncssRegexTargetPath);
		
		if(!dir.isDirectory()) {
			throw new RuntimeException ("ncss.regex.target.path: The path is not a directory");
		}
		
		if(!dir.exists()) {
			throw new RuntimeException ("ncss.regex.target.path: The path doesn't exist");
		}
		
		LOGGER.info("Launch mode:\t\t\t"+ ncssRegexlaunchMode);
		LOGGER.info("Target folder:\t\t\t"+ ncssRegexTargetPath);
		LOGGER.info("Find folder depth:\t\t\t" + ncssRegexFindDepth);
		
		/**
		* Retrieve a stream of Path objects, starting with ncssRegexTargetPath, with a depth of ncssRegexFindDepth subfolders
		* excluding all paths that do not end with .java and which do not contain any string from the ncssRegexExclude list
		 */
		
		try (Stream<Path> stream = Files.find(Paths.get(ncssRegexTargetPath), ncssRegexFindDepth,
	            (path, attr) -> path.getFileName().toString().endsWith(".java")
	            				&& !ncssRegexExclude.stream().anyMatch(path.toString()::contains))) {

			List<Path> javaFileList = stream.collect(Collectors.toCollection(ArrayList::new));

			Map<String,AtomicInteger> fileLinesCountMap = new LinkedHashMap<String,AtomicInteger>();
			
			final Pattern p = Pattern.compile(ncssRegex);
			
			javaFileList.forEach( (path) -> {
				
				final AtomicInteger count = new AtomicInteger();

				try {
					Files.lines(path,Charset.forName(ncssRegexCharset)).forEach((s) -> {
						Matcher matcher = p.matcher(s);
						if((isCountNcss() && !matcher.find()) || (isCountComments() && matcher.find()) || isCountAllLines()){
							count.incrementAndGet();
						}
					});
					fileLinesCountMap.put(path.toString(), count);	
				}
				catch (Exception e) {
					System.out.println("Exception on: "+path);
					e.printStackTrace();
				}
			});
			
			fileLinesCountMap.forEach((k, v) -> LOGGER.info((k + " => " + v)));
		
			AtomicInteger total = new AtomicInteger();
			
			fileLinesCountMap.forEach((k, v) -> total.addAndGet(v.get()));
			
			LOGGER.info("TOTAL ROWS: "+total);
			
		} catch (IOException e) {
		        e.printStackTrace();
		}
		System.out.println(System.currentTimeMillis() - start);
	}
	
	private boolean isCountAllLines() {return ncssRegexlaunchMode.equals(COUNT_ALL_LINES);}

	private boolean isCountComments() {return ncssRegexlaunchMode.equals(COUNT_COMMENTS);}

	private boolean isCountNcss() {return ncssRegexlaunchMode.equals(COUNT_NCSS);}
	

}
