package com.adobe.gdc.checkin.servlet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.jcr.Session;
import javax.servlet.ServletException;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.adobe.gdc.checkin.QuarterlyBDORepositoryClient;
import com.adobe.gdc.checkin.UserManagementService;
import com.adobe.gdc.checkin.constants.QuartelyBDOConstants;

/**
 * @author prajesh
 *         Date: 27/4/14
 *         Time: 9:50 PM
 */

@SlingServlet(resourceTypes={"sling/servlet/default"}, methods={"GET"},selectors={"managerreport"}, extensions = { "html" })
public class GenerateReportsServlet extends SlingSafeMethodsServlet {

    private Logger log = LoggerFactory.getLogger(GenerateReportsServlet.class);
    
    @Reference
	UserManagementService userManagementService;
	
	@Reference
	QuarterlyBDORepositoryClient quarterlyBDORepositoryClient;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {

    	// Read the request parameters
		String managersID = request.getParameter(QuartelyBDOConstants.MANAGERS_ID);
		int quarterNumber = Integer.parseInt(request.getParameter(QuartelyBDOConstants.QUARTER_NUMBER));
		int annualYear = Integer.parseInt(request.getParameter(QuartelyBDOConstants.ANNUAL_YEAR));
		
		log.info("Generating BDO Report for-> managersID="+ managersID+",quarterNumber="+ quarterNumber+ ",annualYear=" + annualYear);
		
		Session session = getSession(request);
    			 
		//Generate data to be written in the file
        Map<String, Object[]> resultData = new TreeMap<String, Object[]>();
        resultData.put("1", new Object[] {"Employee ID", "Name", "Manager Name", "BDO Score for Q"+quarterNumber, "Notes" });
       				
		try{
			//Get All Direct Reportees of the manager
			String[] directReportees = userManagementService.getManagersDirectReportees(managersID,session);
			
			String managerName = userManagementService.getEmployeeName(managersID, session);
	    	
			for(int i=0; i< directReportees.length; i++) {
				Map<String, String[]> employeeBDODataMap = quarterlyBDORepositoryClient.getQuarterlyBDOData(quarterNumber, annualYear, directReportees[i], session);
				//If employee record exists in the repository, get the JSON data
				if(employeeBDODataMap != null && employeeBDODataMap.size() > 0 ) { 
					int index = resultData.size() + 1;
					String[] employeeBDOData = getEmployeeBDOData(employeeBDODataMap, managerName);
					resultData.put(index+"",employeeBDOData);
				}
			}
			writeDataMapToFile(resultData);
		}
		catch(IOException e) {
			log.error("[IOException]",e);
		}
		catch(Exception e) {
			log.error("[Exception]",e);
		}
    }
    
    
    private void writeDataMapToFile( Map<String, Object[]> resultData) throws IOException, Exception{
    	//Blank workbook
		XSSFWorkbook workbook = new XSSFWorkbook(); 
		
		//Create a blank sheet
		XSSFSheet sheet = workbook.createSheet("Employee BDO Report");
		
		//Iterate over data and write to sheet
		Set<String> keyset = resultData.keySet();
		int rownum = 0;
		for (String key : keyset)
		{
		    Row row = sheet.createRow(rownum++);
		    Object [] objArr = resultData.get(key);
		    int cellnum = 0;
		    for (Object obj : objArr)
		    {
		       Cell cell = row.createCell(cellnum++);
		       if(obj instanceof String)
		            cell.setCellValue((String)obj);
		        else if(obj instanceof Integer)
		            cell.setCellValue((Integer)obj);
		    }
		}
	
		//Write the workbook in file system
	    FileOutputStream out = new FileOutputStream(new File("bdo_report.xlsx"));
	    workbook.write(out);
	    out.flush();
	    out.close();
    	log.info("Successfully written generated BDO Report to file->bdo_report.xlsx");    			  
    }
      
    
	private String[] getEmployeeBDOData( Map<String, String[]> employeeBDODataMap, String managerName) throws JSONException {			
		
			String[] employeeBDODataArray = new String[5];
			employeeBDODataArray[0] = employeeBDODataMap.get(QuartelyBDOConstants.EMPLOYEE_ID)!= null 
										? employeeBDODataMap.get(QuartelyBDOConstants.EMPLOYEE_ID)[0]
										:  QuartelyBDOConstants.EMPTY_STRING;
										
			employeeBDODataArray[1] = employeeBDODataMap.get(QuartelyBDOConstants.NAME) != null 
										? employeeBDODataMap.get(QuartelyBDOConstants.NAME)[0]
										: QuartelyBDOConstants.EMPTY_STRING;
										
			employeeBDODataArray[2]	= managerName;
			
			employeeBDODataArray[3]	= employeeBDODataMap.get(QuartelyBDOConstants.BDO_SCORE) != null 
										? employeeBDODataMap.get(QuartelyBDOConstants.BDO_SCORE)[0]
										: QuartelyBDOConstants.EMPTY_STRING;
			
			employeeBDODataArray[4]	= employeeBDODataMap.get(QuartelyBDOConstants.ACHIEVEMENTS) != null
										? employeeBDODataMap.get(QuartelyBDOConstants.ACHIEVEMENTS).toString()
										: QuartelyBDOConstants.EMPTY_STRING;
				
			return employeeBDODataArray;
		}
	
	
    private Session getSession(SlingHttpServletRequest request) {
		return request.getResourceResolver().adaptTo(Session.class);
	}
   
}