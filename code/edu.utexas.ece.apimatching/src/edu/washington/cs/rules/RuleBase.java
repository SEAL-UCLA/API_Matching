/* 
*    API Matching 
*    Copyright (C) <2015>  <Dr. Miryung Kim miryung@cs.ucla.edu>
*
*    This program is free software: you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation, either version 3 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package edu.washington.cs.rules;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.xerces.dom.DOMImplementationImpl;
import org.apache.xerces.parsers.DOMParser;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import edu.washington.cs.util.ListOfPairs;
import edu.washington.cs.util.Pair;
import edu.washington.cs.util.SetOfPairs;

public class RuleBase{
	
	public static PrintStream printStream;
	
	private int sizeSeed =-1; 
	private int sizeSeedLeft =-1; 
	private int sizeDomain = -1;
	private int sizeCodomain = -1; 
	private int sizeSeedMinusAccepted = -1; 
	private int sizeAcceptedMinusSeed = -1;
	private long runningTimeInMillis = 0;
//	
	// a set of distinct acceptedRules that cannot be further unified
	private ArrayList<Rule> acceptedRules;
	// acceptedRule increases every single time 
	
	private ArrayList<JavaMethod> remainingLeft;
	// remainingLeft must up updated every time a rule is accepted 
	
	private HashSet<Rule> candidateRuleQueue;
	// candidasteRuleSortedQueue increases when children are expanded
	
	// a set of distinct pairs generated by applying rules in the acceptedRules
	private SetOfPairs acceptedMatches;
	// acceptedMatches increase every single time 
		
	public final ArrayList<JavaMethod> originalLeft;
	public final ArrayList<JavaMethod> originalRight;
	
	private HashMap<String,Rule> ruleCache =null;
	
	private static final String xmlTag = "rulebase";
	public RuleBase(ArrayList<JavaMethod> leftInitialDomain,
			ArrayList<JavaMethod> rightInitialDomain, ListOfPairs initialSeeds, double EXCEPTION_TH) {
		if (EXCEPTION_TH>0) Rule.setExceptionThreshold(EXCEPTION_TH);
		// set the acceptedRules to be zero 
		this.acceptedRules = new ArrayList<Rule>();
		// set the acceptedMatches to be zero 
		this.acceptedMatches = new SetOfPairs();
		// set the remainingLeft and remainingRight to be the initial domain
		this.remainingLeft = new ArrayList<JavaMethod> ();
		this.remainingLeft.addAll(leftInitialDomain);
		this.originalLeft = new ArrayList<JavaMethod> ();
		this.originalLeft.addAll(leftInitialDomain);
		this.originalRight = rightInitialDomain;
		this.ruleCache = new HashMap<String,Rule>();
		// make initial rules based on seeds;
		makeInitialRules(initialSeeds);
	}
	private void makeInitialRules(ListOfPairs initialSeeds) {
		int count = 0;
		int useless = 0;
		this.candidateRuleQueue = new HashSet<Rule>();
		if (initialSeeds==null || initialSeeds.size()==0) return; 
		// 1. make initial rule
		for (Iterator<Pair> seedIt = initialSeeds.iterator(); seedIt.hasNext();) {
			Pair<JavaMethod> seed = (Pair<JavaMethod>) seedIt.next();
			
			JavaMethod left = seed.getLeft();
			JavaMethod right = seed.getRight();
			List<Change> changes = Change.createChange(left, right);
			
			for (Iterator<Change> changeIt = changes.iterator(); changeIt
					.hasNext();) {
				count++;
				Change change = changeIt.next();
	
				Rule rule = new Rule(left, change, this);
				
				if (rule.isRemovable() == false) {
					// add to a queue 
					if (rule.numPositive() == 1) {
						// if only one match, reduce the scope to the most specific
						rule.getScope().refineMostSpecific();
						rule = new Rule (rule.getScope(),change,this);
					}						
					this.candidateRuleQueue.add(rule);
				}
			}
		}
		debugPrint("Initial Rule\t" + useless + " / " + count + "\t Seed Size"
				+ initialSeeds.size());
		debugFlush();
		emptyCache();
	}
	
	public void testRules() {
		for (Iterator<Rule> candidateIt = this.candidateRuleQueue
				.iterator(); candidateIt.hasNext();) {
			Rule candidate = candidateIt.next();
//			System.out.println(candidate.getStat());
			if (candidate
					.isWorthExpandingChildren(0)) {
				// add its children
				TreeSet<Rule> children = candidate.createChildrenRules(this,
						0, null);
				if (children==null) continue;
				for (Iterator<Rule> childIt = children.iterator(); childIt
						.hasNext();) {
					Rule child = childIt.next();
//					System.out.println(child.getStat());
				}
			}
		}
	}
	public boolean acceptTheBestRule(boolean greaterOne) {
		Rule currentMaxValidRemainingPositiveRule = null;
		if (greaterOne) {
			int currentMaxValidRemainingPositive = 0;
			// 1. scan all candidate rules and get
			// currentMaxValidRemainingPositive
			for (Iterator<Rule> candidateIt = this.candidateRuleQueue
					.iterator(); candidateIt.hasNext();) {
				Rule candidate = candidateIt.next();
				if (candidate.isValidDuringInference()
						&& candidate.numRemainingPositive() > currentMaxValidRemainingPositive) {
					currentMaxValidRemainingPositive = candidate
							.numRemainingPositive();
					currentMaxValidRemainingPositiveRule = candidate;
				}
			}
			debugPrint("Step 1\tMax" + currentMaxValidRemainingPositive);

			// 2. scan all invalid rules and expand its children if the invalid
			// rule
			// is worthwhile to expand.
			ArrayList<Rule> toBeRemoved = new ArrayList<Rule>();
			ArrayList<Rule> toBeAdded = new ArrayList<Rule>();
			for (Iterator<Rule> candidateIt = this.candidateRuleQueue
					.iterator(); candidateIt.hasNext();) {
				Rule candidate = candidateIt.next();
				boolean seedExplainingRule = false;

		
//				if (candidate.getMatches().includeLeft(
//						candidate.getScope().getSeed())) {
//					// seed containing rule
//					seedExplainingRule = true;
//					System.out.println("\n SEED CONTAINING RULE");
//				}
//				System.out.println("Candidate "+ candidate);
//				System.out.println("Seed " + candidate.getScope().getSeed());
				if (candidate
						.isWorthExpandingChildren(currentMaxValidRemainingPositive)) {
					// 5.1.06
					// under the new definition, don't delete the parent
					// candidate rule

					// 4.28.06
					// remove itself
					toBeRemoved.add(candidate);
					boolean childrenExplainSeed = false; 
					
					// add its children
					TreeSet<Rule> children = candidate.createChildrenRules(
							this, currentMaxValidRemainingPositive, null);
					
//					if (seedExplainingRule == true && children==null) { 
//						System.out.println("Seed Explaining Rule Died without Producing Children");
//						System.out.println(seedExplainingRule);
//					}
					if (children == null)
						continue;
					// System.out.println(children.size());
					// make sure all rules are valid
					// update currentMaxValidRemaingPositive as needed
					for (Iterator<Rule> childIt = children.iterator(); childIt
							.hasNext();) {
						Rule child = childIt.next();
						if (child.getMatches().includeLeft(candidate.getScope().getSeed())) { 
							childrenExplainSeed = true;
						}
						if (child.isValidDuringInference()
								
								&& child.numRemainingPositive() > currentMaxValidRemainingPositive) {
							currentMaxValidRemainingPositive = child
									.numRemainingPositive();
							currentMaxValidRemainingPositiveRule = child;
							debugPrint("Updating\t"
									+ currentMaxValidRemainingPositive);
						} else if (child.isRemovable()) {
							System.out.println("Impossible");
						}
					}
//					if (seedExplainingRule==true && childrenExplainSeed==false) { 
//						System.out.println("Seed Explaining Rule Did not produce any children that explain Seed");
//					}
					toBeAdded.addAll(children);
				}// expansion of children
			}
			debugPrint("Step 3\tAdd" + toBeAdded.size() + "\tRemove"
					+ toBeRemoved.size());
			// 3. add toBeAdded to the candidateRuleQueue;
			this.candidateRuleQueue.addAll(toBeAdded);
			// 3. remove toBeRemoved from the candidateRuleQueue
			this.candidateRuleQueue.removeAll(toBeRemoved);
		
		debugPrint("Step 4\tMax"+currentMaxValidRemainingPositive);
		
		}else { // not greater than one 
			currentMaxValidRemainingPositiveRule = this.candidateRuleQueue.iterator().next();
		}
		// 4. accept the best rule as updating the
		// currentMaxValidRemaingPositive
		if (currentMaxValidRemainingPositiveRule == null)
			return false;
		// 4.1. update acceptedRules
		this.acceptedRules.add(currentMaxValidRemainingPositiveRule);
		if (currentMaxValidRemainingPositiveRule.numRemainingPositive()==1){
			greaterOne=false;
		}
		debugPrint("\tAccepting"+currentMaxValidRemainingPositiveRule.getStat());
		//System.out.println("\tAccepting"+currentMaxValidRemainingPositiveRule);
		
		// 4.2. update candidateRule
		this.candidateRuleQueue
				.remove(currentMaxValidRemainingPositiveRule);
		// 4.3. update acceptedMatches
		// 4.3.1 remember what additional positive things it matches 
		
		ListOfPairs additionalMatched = new ListOfPairs ();
		for (Iterator<Pair> pairIt= currentMaxValidRemainingPositiveRule.getMatches().iterator(); pairIt.hasNext(); )  {
			Pair pair =pairIt.next();
			if (!this.acceptedMatches.contains(pair)) {
				additionalMatched.addPair(pair);
				this.acceptedMatches.addPair(pair);
			}
		}
		
		// 4.4. update remainingLeft
		this.remainingLeft.removeAll(additionalMatched.getLeftDomain());
		// 4.5. update all rules in candidateRuleQueue
		debugPrint("Step 4\tQueue Size"+this.candidateRuleQueue.size());
		
		// empty rule cache because remainingLeft has changed
		emptyCache();
		ArrayList<Rule> toBeRemoved = new ArrayList<Rule>();
		int useless= 0;
		for (Iterator<Rule> candidateIt= this.candidateRuleQueue.iterator(); candidateIt.hasNext(); ){ 
			Rule candidate = candidateIt.next();
			// optimization, don't recompute if the same rule is already computed  
			if (isHit(candidate.getID())) {
				Rule alreadyHit = retrieveCache(candidate.getID());
				candidate.updateFrom(alreadyHit);
			}else {
				candidate.recompute(additionalMatched); // update conflictPositive and remainingPositive
				pushCache(candidate);
			}
			boolean toRemove = candidate.isRemovable(); 
			if (toRemove){
				toBeRemoved.add(candidate);
				
				// Can it remove rules where it seed.left is not in the accepted matches? 
				// Yes. some rules where its seed.right is not explained by transformations. 
				// P8 N5 rP 0 where composite transformation can only explain seeds. 
//				
//				Set<JavaMethod> acceptedLeft = this.acceptedMatches.getLeftDomain();
//				JavaMethod seedL = (candidate.getScope().getSeed());
//				
//				if  (!acceptedLeft.contains(seedL) && candidate.getMatches().getByLeft(seedL)!=null){
//					// SEED INVESTIGATION
//					// acceptedLeft does not contain seedL
//					// yet this candidate rule's positive matches contains seedMatch, thus getting rejected 
//					System.out.println("Accepted So Far "+acceptedLeft.size());
//					System.out.println("SeedL "+ seedL);
//					System.out.println("Rule " + candidate.toString());
//					System.out.println("Rule NEG" + candidate.getNegativeMatches(this));
//					System.exit(0);
//				}
				useless++;
			}
		}
		debugPrint("\nStep 5\tUseless\t"+useless);
		this.candidateRuleQueue.removeAll(toBeRemoved);
		debugPrint("Step 6\tRemainLeft\t"+this.remainingLeft.size());
		debugFlush();
		return greaterOne;
	} 
	

	public void addRule (Rule rule) {
		// 4. accept the best rule as updating the
		// currentMaxValidRemaingPositive
		if (rule == null)
			return;
		// 4.1. update acceptedRules
		this.acceptedRules.add(rule);
		//System.out.println("\tAccepting"+currentMaxValidRemainingPositiveRule);
		
		// 4.2. update candidateRule
		this.candidateRuleQueue
				.remove(rule);
		// 4.3. update acceptedMatches
		// 4.3.1 remember what additional positive things it matches 
		
		ListOfPairs additionalMatched = new ListOfPairs ();
		for (Iterator<Pair> pairIt= rule.getMatches().iterator(); pairIt.hasNext(); )  {
			Pair pair =pairIt.next();
			if (!this.acceptedMatches.contains(pair)) {
				additionalMatched.addPair(pair);
				this.acceptedMatches.addPair(pair);
			}
		}
		
		// 4.4. update remainingLeft
		this.remainingLeft.removeAll(additionalMatched.getLeftDomain());
		// 4.5. update all rules in candidateRuleQueue
		
	}
	public void printAcceptedRules () {
		debugPrint("Printing Accepted Rule Size:"+this.acceptedRules.size());
		for (Iterator<Rule> acceptedRule = this.acceptedRules.iterator(); acceptedRule
				.hasNext();) {
			debugPrint(acceptedRule.next().toString());
		}
	}
	public void printAcceptedRulesWithNegativeMatches(PrintStream stream) {
		HashMap<Transformation, ArrayList<Rule>> map = new HashMap<Transformation, ArrayList<Rule>>();
		// create a map for each transformation
		for (int i = 0; i < this.acceptedRules.size(); i++) {
			Rule rule = this.acceptedRules.get(i);
			rule.mapByTransformation(map);
		}
		if (stream != null) {
			stream.println("Printing Accepted Rule Size:" + map.size());
			int count =0; 
			for (Iterator<Transformation> it = map.keySet().iterator(); it
					.hasNext();) {
				Transformation c = it.next();
				ArrayList<Rule> rs = (ArrayList<Rule>) map.get(c);
				ScopeDisjunction disjScope = new ScopeDisjunction();
				for (int i = 0; i < rs.size(); i++) {
					Rule rule = rs.get(i);
					disjScope.add(rule.getScope());
				}
				stream.println("\nRule "+count); count++;
				stream.println(disjScope.toString());
				stream.println(c);
				SetOfPairs positiveMatches = new SetOfPairs();
				SetOfPairs negativeMatches = new SetOfPairs();
				for (int i=0; i<rs.size(); i++)  {
					Rule rule = rs.get(i);
					ListOfPairs pM = rule.getMatches();
					ListOfPairs nM = rule.getNegativeMatches(this);
					positiveMatches.addSetOfPairs(pM);
					negativeMatches.addSetOfPairs(nM);
				}
				stream.println("P:"+positiveMatches.size()+"\tN:"+negativeMatches.size());
				if (negativeMatches.size()>0) {
					stream.println("POSITIVE");
					stream.println(positiveMatches);
					stream.println("NEGATIVE");
					stream.println(negativeMatches);
				}
				
				stream.println();
				stream.flush();
			}
		}
		
	}
	public ArrayList<Rule> getAcceptedRules() {
		return this.acceptedRules;
	}
	public SetOfPairs getAcceptedMatches() { 
		return this.acceptedMatches;
	}
	
	public ArrayList<JavaMethod> getRemainingLeft() { 
		return this.remainingLeft;
	}
	
	private static void debugPrint (String s) { 
		if (printStream!=null) printStream.println(s);
//		if (printStream!=null)

	}
	private static void debugFlush () {
		if (printStream!=null) printStream.flush();
		System.out.flush();
	}
	public static void main (String args[]) {
		String methodPair[] = {
				
				"com.jrefinery.chart.axis:TickUnit-compareTo__[Object]->int",
				"com.jrefinery.chart:TickUnit-compareTo__[Object]->int",
				
				"com.jrefinery.chart.plot:XYPlot-draw__[Graphics2D, Rectangle2D, ChartRenderingInfo]->void",
				"com.jrefinery.chart:XYPlot-draw__[Graphics2D, Rectangle2D, ChartRenderingInfo]->void",
				
				"com.jrefinery.chart.renderer:StackedHorizontalBarRenderer-StackedHorizontalBarRenderer__[CategoryToolTipGenerator, CategoryURLGenerator]->void",
				"com.jrefinery.chart:StackedHorizontalBarRenderer-StackedHorizontalBarRenderer__[]->void",
				
				"com.jrefinery.chart.renderer:StackedVerticalBarRenderer-StackedVerticalBarRenderer__[CategoryToolTipGenerator, CategoryURLGenerator]->void",
				"com.jrefinery.chart:StackedVerticalBarRenderer-StackedVerticalBarRenderer__[]->void",
				
				"com.jrefinery.chart:ChartFactory-ChartFactory__[String, ChartFactory[], ChartFactory[], XYDataset, boolean]->ChartFactory[]",
				"com.jrefinery.chart:Chart-Chart__[String, Chart[], Chart[], XYDataset, boolean]->Chart[]",
				
				"com.jrefinery.chart:ChartFactory$A-createStackedHorizontalBarChart$B__[String, String, String, CategoryDataset, boolean, boolean, boolean]->JFreeChart",
				"com.jrefinery.chart:ChartFactory-createStackedHorizontalBarChart__[String, String, String, CategoryDataset, boolean]->JFreeChart",
				
				"com.jrefinery.chart:ChartFactory-createStackedVerticalBarChart__[String, String, String, CategoryDataset, boolean, boolean, boolean]->JFreeChart",
				"com.jrefinery.chart:ChartFactory-createStackedVerticalBarChart__[String, String, String, CategoryDataset, boolean]->JFreeChart",
				
				"com.jrefinery.chart:ChartFactory-createVerticalBarChart3D__[String, String, String, CategoryDataset, boolean, boolean, boolean]->JFreeChart",
				"com.jrefinery.chart:ChartFactory-createVerticalBarChart3D__[String, String, String, CategoryDataset, boolean]->JFreeChart",
				
				"com.jrefinery.chart:Legend-Legend__[JFreeChart]->void",
				"com.jrefinery.chart:Legend-Legend__[JFreeChart, int]->void",
				
				"com.jrefinery.chart:StandardLegend-StandardLegend__[JFreeChart, Spacer, Spacer, Paint, Stroke, Paint, Font, Paint]->void",
				"com.jrefinery.chart:StandardLegend-StandardLegend__[JFreeChart, int, Spacer, Paint, Stroke, Paint, Font, Paint]->void",
				
				"com.jrefinery.chart:ChartFactory-createScatterPlot__[String, String, String, XYDataset, boolean]->JFreeChart",
				"com.jrefinery.chart:ChartFactory-createScatterPlot__[String, String, String, XYDataset, boolean, boolean, boolean]->JFreeChart",
				
				"com.jrefinery.chart:AbstractXYItemRenderer-getPlot__[]->XYPlot", 
				"com.jrefinery.chart.renderer:XYItemRenderer-getPlot__[]->Plot",
				
				"com.jrefinery.chart:StandardXYItemRenderer-getDefaultShapeScale__[]->double",
				"com.jrefinery.chart.renderer:StandardXYItemRenderer-getDefaultShapeFilled__[]->boolean",
				
				"com.jrefinery.chart:StandardXYItemRenderer-getShapeScale__[Plot, int, int, double, double]->double",
				"com.jrefinery.chart.renderer:StandardXYItemRenderer-getImage__[Plot, int, int, double, double]->Image",
				
				"com.jrefinery.chart:ValueAxis-getAutoRangeMinimumSize__[]->Number",
				"com.jrefinery.chart.axis:ValueAxis-getAutoRangeMinimumSize__[]->double",
				
				"com.jrefinery.data:DefaultCategoryDataset-setSeriesName__[int, String]->void",
				"com.jrefinery.data:DefaultIntervalCategoryDataset-setSeriesKeys__[Comparable[]]->void",
				
				"org.jfree.chart.labels:StandardXYItemLabelGenerator-StandardXYItemLabelGenerator__[String, String, DateFormat, NumberFormat]->void",
				"org.jfree.chart.labels:StandardXYLabelGenerator-StandardXYLabelGenerator__[String, DateFormat, DateFormat]->void",
				
				"org.jfree.chart.renderer:AbstractCategoryItemRenderer-getRangeType__[]->RangeType",
				"org.jfree.chart.renderer:AbstractCategoryItemRenderer-getRangeExtent__[CategoryDataset]->Range",
				
				"org.jfree.chart.renderer:StackedBarRenderer3D-getRangeType__[]->RangeType",
				"org.jfree.chart.renderer:StackedBarRenderer3D-getRangeExtent__[CategoryDataset]->Range",
				
				"org.jfree.chart.renderer:WaterfallBarRenderer-getRangeType__[]->RangeType",
				"org.jfree.chart.renderer:WaterfallBarRenderer-getRangeExtent__[CategoryDataset]->Range",
				
				"org.jfree.chart.renderer:XYAreaRendererState-XYAreaRendererState__[PlotRenderingInfo]->void",
				"org.jfree.chart.renderer:XYAreaRenderer$XYAreaRendererState-XYAreaRendererState__[PlotRenderingInfo]->void",
				
				"org.jfree.chart.axis:CategoryAxis-drawHorizontalCategoryLabels__[Graphics2D, double, Rectangle2D, Rectangle2D, RectangleEdge]->double",
				"org.jfree.chart.axis:CategoryAxis-drawCategoryLabels__[Graphics2D, Rectangle2D, Rectangle2D, RectangleEdge, AxisState]->AxisState" 				
		};

		JavaMethod[] jmList = new JavaMethod[methodPair.length];
		ArrayList<JavaMethod> oldMethods = new ArrayList<JavaMethod>();
		ArrayList<JavaMethod> newMethods = new ArrayList<JavaMethod>();
		ListOfPairs matchedPairs = new ListOfPairs();
		for (int i = 0; i < methodPair.length; i= i+2) {
			jmList[i] = new JavaMethod(methodPair[i]);
			jmList[i+1] = new JavaMethod(methodPair[i+1]);
			oldMethods.add(jmList[i]);
			newMethods.add(jmList[i+1]);
			Pair<JavaMethod> p = new Pair<JavaMethod>(jmList[i],jmList[i+1]);
			matchedPairs.addPair(p);
		}
		RuleBase rb = new RuleBase(oldMethods,newMethods,matchedPairs, 0.25);
		rb.writeXMLFile("temp");
		RuleBase copy = RuleBase.readXMLFile("temp");
		if (!copy.equals(rb)) {
			System.out.println("ERROR");
			System.exit(0);
		}
		boolean cont = true;
		int matches = rb.getAcceptedMatches().size();
		while (cont) {
			rb.acceptTheBestRule(true);
			int newmatches = rb.getAcceptedMatches().size();
			if (newmatches>matches) { 
				matches = newmatches;
			}else {
				cont = false;
			}
		}
		rb.writeXMLFile("temp");
		copy = RuleBase.readXMLFile("temp");
		if (!copy.equals(rb)) {
			System.out.println("ERROR");
			System.exit(0);
		}
		
		for (Iterator<Rule> acceptedRule = rb.acceptedRules.iterator(); acceptedRule.hasNext(); ){ 
			debugPrint(acceptedRule.next().toString());
		}
		debugPrint(matchedPairs.includeAll(rb.acceptedMatches)+"");
		for (Iterator<Pair> pairIt = matchedPairs.iterator(); pairIt.hasNext(); ) { 
			Pair p = pairIt.next();
			if (!rb.acceptedMatches.contains(p)) { 
				debugPrint(p.toString());
			}
		}
		debugPrint(rb.acceptedMatches.includeAll(matchedPairs)+"");
		for (Iterator<Pair> pairIt = rb.acceptedMatches.iterator(); pairIt.hasNext(); ) { 
			Pair p = pairIt.next();
			if (!matchedPairs.contains(p)) { 
				debugPrint(p.toString());
			}
		}
	}
	boolean isHit(String id) {  
		if (ruleCache.containsKey(id)) {
//			System.out.print("H");
			return true;
		}
		return false;
	}
	void pushCache (Rule rule) { 
		ruleCache.put(rule.getID(),rule);	
	}
	Rule retrieveCache (String id) { 
		return ruleCache.get(id);
	}
	private void emptyCache () { 
		this.ruleCache.clear();
	}
	public int numAcceptedRule() { 
		return this.acceptedRules.size();
	}
	public int numRemainingLeft() { 
		return this.remainingLeft.size();
	}
	public String ruleDistribution () { 
		int numRules[] = new int[1000];
		for (Iterator<Rule> ruleIt = this.acceptedRules.iterator(); ruleIt
				.hasNext();) {
			Rule rule = ruleIt.next();
			if (rule.numPositive()<1000) numRules[rule.numPositive()]++;
		}
		String s ="\n";
		for (int i=0; i< 1000 ; i++) { 
			if (numRules[i]==0) continue;
			s = s +i+"\t"+numRules[i]+"\n";
		}
		return s;
	}

	public void writeXMLFile (String filename) { 
		System.out.println("Writing a rule to a file:\t"+filename); 
		Document doc =
			DOMImplementationImpl.getDOMImplementation().createDocument(
				"namespaceURI",
				"changelist",
				null);
		// update document
		writeElement(doc.getDocumentElement());
		File file = new File(filename);
		try {	
			if (!file.exists()) file.createNewFile();
			//serialize DOM document to outputfile 
			XMLSerializer serializer = new XMLSerializer();
			OutputFormat format = new OutputFormat();
			format.setPreserveSpace(true);
			format.setPreserveEmptyAttributes(true);
			format.setIndenting(true);
			format.setIndent(4);
			format.setLineWidth(80);
			format.setLineSeparator("\n");
			String[] nonEscapingElements = { "\n", "\t" };
			format.setNonEscapingElements(nonEscapingElements);
			serializer = new XMLSerializer(format);
			FileOutputStream outstream = new FileOutputStream(file);
			serializer.setOutputByteStream(outstream);
			assert (doc!= null);
			serializer.serialize(doc);
			outstream.close();
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	public static RuleBase readXMLFile (String filename){
		Document doc = null;
		DOMParser domparser = new DOMParser();
		try {
			domparser.parse(filename);
			doc = domparser.getDocument();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SAXException s) {
			s.printStackTrace();
		}
		// parse doc and return GroupMap
		return readElement((Element)doc.getDocumentElement().getFirstChild());
	}

	public static RuleBase readElement (Element rb) {
		if (!rb.getTagName().equals(xmlTag)) return null;
		NodeList children = rb.getChildNodes();
		SetOfPairs aM = null;
		ArrayList<JavaMethod> oL = new ArrayList<JavaMethod > ();
		ArrayList<JavaMethod> oR = new ArrayList<JavaMethod> ();
		ArrayList<JavaMethod> rL = new ArrayList<JavaMethod> ();
		ArrayList<Rule> aRule = new ArrayList<Rule> (); 

		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i) instanceof Element) {
				Element child = (Element) children.item(i);
				if (child.getTagName().equals(SetOfPairs.getXMLTag())) {
					aM= SetOfPairs.readElement(child);
				} else if (child.getTagName().equals(Rule.getXMLTag())) {
					Rule rule = Rule.readElement(child);
					aRule.add(rule);
				} else if (child.getTagName().equals("originalleft")) {
					JavaMethod m = new JavaMethod(child.getTextContent());
					oL.add(m);
				} else if (child.getTagName().equals("originalright")) {
					JavaMethod m = new JavaMethod(child.getTextContent());
					oR.add(m);
				} else if (child.getTagName().equals("remainingleft")) {
					JavaMethod m = new JavaMethod(child.getTextContent());
					rL.add(m)
;				}
			}
		}
//		System.out.println("ol:"+oL.size()+"or:"+oR.size()+"rL:"+rL.size());
		RuleBase rulebase = new RuleBase(oL,oR,null, 0);
		rulebase.acceptedMatches=aM;
		rulebase.acceptedRules=aRule;
		rulebase.remainingLeft = rL;

		if (!rb.getAttribute("sizeSeed").equals("")) {
			rulebase.setSizeSeed(new Integer(rb.getAttribute("sizeSeed")).intValue());
		}
		if (!rb.getAttribute("sizeSeedLeft").equals("")) {
			rulebase.setSizeSeedLeft(new Integer(rb.getAttribute("sizeSeedLeft")).intValue());
		}
		if (!rb.getAttribute("sizeDomain").equals("")) {
			rulebase.setSizeDomain(new Integer(rb.getAttribute("sizeDomain")).intValue());
		}
		if (!rb.getAttribute("sizeCodomain").equals("")) {
			rulebase.setSizeCodomain(new Integer(rb.getAttribute("sizeCodomain")).intValue());
		}
		if (!rb.getAttribute("sizeAcceptedMinusSeed").equals("")) {
			rulebase.setSizeAcceptedMinusSeed(new Integer(rb.getAttribute("sizeAcceptedMinusSeed")).intValue());
		}
		if (!rb.getAttribute("sizeSeedMinusAccepted").equals("")) {
			rulebase.setSizeSeedMinusAccepted(new Integer(rb.getAttribute("sizeSeedMinusAccepted")).intValue());
		}	
		if (!rb.getAttribute("runningTimeInMillis").equals("")) {
			rulebase.setRunningTimeInMillis(new Long(rb.getAttribute("runningTimeInMillis")).longValue());
		}
		return rulebase;
	}
	public void writeElement (Element parent ) {
		Element rb = parent.getOwnerDocument().createElement(xmlTag);
		if (sizeSeed !=-1) rb.setAttribute("sizeSeed",new Integer(sizeSeed).toString());
		if (sizeSeedLeft !=-1) rb.setAttribute("sizeSeedLeft", new Integer(sizeSeedLeft).toString());
		if (sizeDomain !=-1) rb.setAttribute("sizeDomain", new Integer(sizeDomain).toString());
		if (sizeCodomain !=-1) rb.setAttribute("sizeCodomain", new Integer(sizeCodomain).toString());
		if (sizeAcceptedMinusSeed!=-1) rb.setAttribute("sizeAcceptedMinusSeed", new Integer(sizeAcceptedMinusSeed).toString());
		if (sizeSeedMinusAccepted!=-1) rb.setAttribute("sizeSeedMinusAccepted", new Integer(sizeSeedMinusAccepted).toString());
		if (runningTimeInMillis !=0) rb.setAttribute("runningTimeInMillis", new Long(runningTimeInMillis).toString());
		this.acceptedMatches.writeElement(rb);
		for (int i = 0; i< acceptedRules.size(); i++ ) {
			Rule r = acceptedRules.get(i);
			r.writeElement(rb);
		}
		for (int i= 0; i<originalLeft.size(); i++) {
			JavaMethod m = originalLeft.get(i);
			Element ol = parent.getOwnerDocument().createElement("originalleft");
			ol.setTextContent(m.toString());
			rb.appendChild(ol);
		}
		for (int i = 0; i<originalRight.size(); i++) {
			JavaMethod m = originalRight.get(i);
			Element or = parent.getOwnerDocument().createElement("originalright");
			or.setTextContent(m.toString());
			rb.appendChild(or);
		}
		for (int i = 0; i < remainingLeft.size(); i++) {
			JavaMethod m = remainingLeft.get(i);
			Element rl = parent.getOwnerDocument().createElement(
					"remainingleft");
			rl.setTextContent(m.toString());
			rb.appendChild(rl);
		}
		parent.appendChild(rb);
	}
	public boolean equals(Object other) {
		if (other instanceof RuleBase) {
			RuleBase rbo = (RuleBase) other;
			return (
					this.acceptedMatches.includeAll(rbo.acceptedMatches)&&
			rbo.acceptedMatches.includeAll(this.acceptedMatches) &&
			this.acceptedRules.containsAll(rbo.acceptedRules) &&
			this.remainingLeft.containsAll(rbo.remainingLeft) &&
			this.originalLeft.containsAll(rbo.originalLeft) &&
			this.originalRight.containsAll(rbo.originalRight)
			);
		}
		return false;
	}
	public void cleanUpScope() { 
		for (int i=0;i<acceptedRules.size();i++) { 
			Rule r = acceptedRules.get(i);
			if (r.getMatches().size()==1) { 
				r.getScope().refineMostSpecific();
			}
		}
	}
	
	public void rulesSubsumedByOthers() { 
		for (int i=0; i<acceptedRules.size();i++) { 
			Rule ri = acceptedRules.get(i);
			ListOfPairs mri = ri.getMatches();
			Set<JavaMethod> mri_left = mri.getLeftDomain();
			for (int j=0; j<acceptedRules.size(); j++) {
				if (j==i) continue;
				Rule rj = acceptedRules.get(j);
				ListOfPairs mrj = rj.getMatches();
				Set<JavaMethod> mrj_left = mrj.getLeftDomain();
				if (mrj_left.containsAll(mri_left)) { 
					System.out.println("mri: "+mri);
					System.out.println("contained by: "+mrj);
				}
			}
		}
	}
	/*
	 * input: rulebase 
	 * output: create matches based by applying all applicable single 
	 * transformation-based rules. 
	 */
	public RuleBase reproduceMatchesUsingTransformationRule() { 
		RuleBase newrb = new RuleBase(this.originalLeft, this.originalRight,
				null, 0);
		// iterate over all originalLeft
		for (Iterator<JavaMethod> leftIt = this.originalLeft.iterator(); leftIt
				.hasNext();) {
			JavaMethod jmLeft = leftIt.next();
			JavaMethod converted = new JavaMethod(jmLeft.toString());
			for (Iterator<Rule> ruleIt = this.acceptedRules.iterator(); ruleIt
					.hasNext();) {
				Rule rule = ruleIt.next();
				if (rule.isApplicable(jmLeft)) {
					System.out.println(rule.getID());
					converted = rule.getChange().applyTransformation(converted);
				}
			}
			if (this.originalRight.contains(converted)) { 
				newrb.acceptedMatches.addPair(new Pair(jmLeft,converted));
			}else { 
				if (!this.remainingLeft.contains(jmLeft)) {
			
				}
				newrb.remainingLeft.add(jmLeft);
			}
		}
		boolean b1 = newrb.acceptedMatches.includeAll(this.acceptedMatches);
		boolean b2 = this.acceptedMatches.includeAll(newrb.acceptedMatches);
		boolean b3 = newrb.remainingLeft.containsAll(this.remainingLeft);
		boolean b4 = this.remainingLeft.containsAll(newrb.remainingLeft);
		System.out.println(b1+"\t"+b2+"\t"+b3+"\t"+b4);
		return newrb;
	}
	public int getSizeAcceptedMinusSeed() {
		return sizeAcceptedMinusSeed;
	}
	public long getRunningTimeInMillis() {
		return runningTimeInMillis;
	}
	public int getSizeSeedMinusAccepted() {
		return sizeSeedMinusAccepted;
	}
	public int getSizeCodomain() {
		return sizeCodomain;
	}
	public int getSizeDomain() {
		return sizeDomain;
	}
	public int getSizeSeed() {
		return sizeSeed;
	}
	public int getSizeSeedLeft() {
		return sizeSeedLeft;
	}
	public void setSizeAcceptedMinusSeed(int acceptedMinusSeed) {
		this.sizeAcceptedMinusSeed = acceptedMinusSeed;
	}
	public void setRunningTimeInMillis(long runningTimeInMillis) {
		this.runningTimeInMillis = runningTimeInMillis;
	}
	public void setSizeSeedMinusAccepted(int seedMinusAccepted) {
		this.sizeSeedMinusAccepted = seedMinusAccepted;
	}
	public void setSizeCodomain(int sizeCodomain) {
		this.sizeCodomain = sizeCodomain;
	}
	public void setSizeDomain(int sizeDomain) {
		this.sizeDomain = sizeDomain;
	}
	public void setSizeSeed(int sizeSeed) {
		this.sizeSeed = sizeSeed;
	}
	public void setSizeSeedLeft(int sizeSeedLeft) {
		this.sizeSeedLeft = sizeSeedLeft;
	}

	
}