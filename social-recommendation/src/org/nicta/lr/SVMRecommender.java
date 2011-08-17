package org.nicta.lr;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.nicta.lr.util.Constants;
import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.RecommenderUtil;
import org.nicta.lr.util.UserUtil;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_problem;
import libsvm.svm_parameter;
import java.util.ArrayList;

public class SVMRecommender extends LinkRecommender
{
	Object[] userIds;
	Object[] linkIds;
	
	public SVMRecommender()
	{
		super(null);
	}
	
	public static void main(String[] args)
		throws Exception
	{
		SVMRecommender svm = new SVMRecommender();
		svm.crossValidate();
		//svm.recommend();
	}
	
	public void crossValidate()
		throws Exception
	{
		HashMap<Long, Double[]> users = UserUtil.getUserFeatures();
		System.out.println("Retrieved users: " + users.size());
		
		HashMap<Long, Double[]> links = LinkUtil.getLinkFeatures(true);
		System.out.println("Retrieved links: " + links.size());
		
		HashMap<Long, Long[]> linkUsers = LinkUtil.getUnormalizedFeatures(links.keySet());
		HashMap<Long, HashSet<Long>> linkLikes = LinkUtil.getLinkLikes(linkUsers, true);
		HashMap<Long, HashMap<Long, Double>> friendships = UserUtil.getFriendships();
		
		HashMap<Long, HashSet<Long>> userLinkSamples = RecommenderUtil.getUserLinksSample(linkLikes, users.keySet(), friendships, linkUsers, false);
		System.out.println("Samples: " + userLinkSamples.size());
		
		userIds = userLinkSamples.keySet().toArray();
		linkIds = links.keySet().toArray();
	
		RecommenderUtil.closeSqlConnection();
		
		HashMap<Long, HashSet<Long>> tested = new HashMap<Long, HashSet<Long>>();
		for (long userId : userLinkSamples.keySet()) {
			tested.put(userId, new HashSet<Long>());
		}
		
		double totalTruePos = 0;
		double totalFalsePos = 0;
		double totalTrueNeg = 0;
		double totalFalseNeg = 0;
		
		HashMap<Long, Double> averagePrecision = new HashMap<Long, Double>();
		
		//for (int x = 0; x < 10; x++) {
			HashMap<Long, HashSet<Long>> forTesting = new HashMap<Long, HashSet<Long>>();
			
			for (long userId : userLinkSamples.keySet()) {
				HashSet<Long> userTesting = new HashSet<Long>();
				forTesting.put(userId, userTesting);
				
				HashSet<Long> samples = userLinkSamples.get(userId);
				HashSet<Long> userTested = tested.get(userId);
				
				
				Object[] sampleArray = samples.toArray();
				
				int addedCount = 0;
				
				int likeCount = 0;
				
				System.out.println("Size: " + sampleArray.length);
				while (addedCount < sampleArray.length * .2 || addedCount < 2) {
					
					if (sampleArray.length == userTested.size()) break;
					
					int randomIndex = (int)(Math.random() * (sampleArray.length));
					Long randomLinkId = (Long)sampleArray[randomIndex];	
					
					if (!tested.get(userId).contains(randomLinkId) && ! userTesting.contains(randomLinkId)) {
						
						if (likeCount == 0) {
							if (!linkLikes.get(randomLinkId).contains(userId)) {
								continue;
							}
							else {
								likeCount++;
							}
						}
						else {		
							if (linkLikes.get(randomLinkId).contains(userId)) {
								int remainingLike = 0;
								for (long remainingId : samples) {
									if (linkLikes.get(remainingId).contains(userId)) remainingLike++;
								}
								
								if (remainingLike == 1) {
									continue;
								}
								else {
									likeCount++;
								}
							}
						}
						
						
						userTesting.add(randomLinkId);
						tested.get(userId).add(randomLinkId);
						samples.remove(randomLinkId);
						addedCount++;
					}
				}
				
				System.out.println("ADDED: " + userTesting.size() + "     " + addedCount);
				if (addedCount != userTesting.size()) {
					System.out.println("Taninga");
					System.exit(1);
				}
				
				if (addedCount <= 1) {
					System.out.println("WHAT THE FUCK");
					System.out.println("Samples: " + samples.size());
					System.exit(1);
				}
				System.out.println();
			}
			
			System.out.println("Training");
			svm_model model = trainSVM(linkLikes, users, links, friendships, userLinkSamples);
			//int[] stats = testSVM(model, linkLikes, users, links, friendships, forTesting);
			
			//int truePos = stats[0];
			//int falsePos = stats[1];
			//int trueNeg = stats[2];
			//int falseNeg = stats[3];
			
			//totalTruePos += truePos;
			//totalFalsePos += falsePos;
			//totalTrueNeg += trueNeg;
			//totalFalseNeg += falseNeg;
			
			HashMap<Long, Double> precisions = getAveragePrecision(model, linkLikes, users, links, friendships, forTesting);
			
			for (long userId : precisions.keySet()) {
				double ap = precisions.get(userId);
				//if (ap == 0) continue;
				
				if (!averagePrecision.containsKey(userId)) {
					averagePrecision.put(userId, 0.0);
				}
				
				double average = averagePrecision.get(userId);
				average += ap;
				
				//System.out.println("average: " + average);
				
				averagePrecision.put(userId, average);
			}
			
			//System.out.println("Stats for Run " + (x+1));
			//System.out.println("True Pos: "+ truePos);
			//System.out.println("False Pos: "+ falsePos);
			//System.out.println("True Neg: "+ trueNeg);
			//System.out.println("False Neg: "+ falseNeg);
			//System.out.println("");
			
			for (long userId : forTesting.keySet()) {
				HashSet<Long> tests = forTesting.get(userId);
				for (long linkId : tests) {
					userLinkSamples.get(userId).add(linkId);
				}
			}
		//}
		
		
		double accuracy = (double)(totalTruePos + totalTrueNeg) / (double)(totalTruePos + totalFalsePos + totalTrueNeg + totalFalseNeg);
		double precision = (double)totalTruePos / (double)(totalTruePos + totalFalsePos);
		double recall = (double)totalTruePos / (double)(totalTruePos + totalFalseNeg);
		double f1 = 2 * precision * recall / (precision + recall);
		
		double map = 0;
		for (long userId : averagePrecision.keySet()) {
			double pre = averagePrecision.get(userId);
			//pre /= (double)10;
			
			map += pre;
		}
		map /= (double)averagePrecision.size();
		double standardDev = 0;
		for (long userId : averagePrecision.keySet()) {
			double pre = averagePrecision.get(userId);
			standardDev += Math.pow(pre - map, 2);
		}
		standardDev /= (double)averagePrecision.size();
		standardDev = Math.sqrt(standardDev);
		double standardError = standardDev / Math.sqrt(averagePrecision.size());
		
		
		System.out.println("C=" + Constants.C);
		//System.out.println("Accuracy: " + accuracy);
		//System.out.println("Precision: " + precision);
		//System.out.println("Recall: " + recall);
		//System.out.println("F1: " + f1);
		System.out.println("MAP: " + map);
		System.out.println("SD: " + standardDev);
		System.out.println("SE: " + standardError);
		System.out.println("");
	}
	
	public void recommend()
		throws Exception
	{
		System.out.println("Loading Data..." + new Date());
		
		HashMap<Long, Double[]> users = UserUtil.getUserFeatures();
		System.out.println("Retrieved users: " + users.size());
		
		HashMap<Long, Double[]> links = LinkUtil.getLinkFeatures(true);
		System.out.println("Retrieved links: " + links.size());
		
		HashMap<Long, Long[]> linkUsers = LinkUtil.getUnormalizedFeatures(links.keySet());
		HashMap<Long, HashSet<Long>> linkLikes = LinkUtil.getLinkLikes(linkUsers, true);
		HashMap<Long, HashMap<Long, Double>> friendships = UserUtil.getFriendships();	
		
		HashMap<Long, HashSet<Long>> userLinkSamples = RecommenderUtil.getUserLinksSample(linkLikes, users.keySet(), friendships, linkUsers, true);
		System.out.println("users: " + userLinkSamples.size());
		
		userIds = userLinkSamples.keySet().toArray();
		linkIds = links.keySet().toArray();
		
		RecommenderUtil.closeSqlConnection();
		
		System.out.println("Training...");
		svm_model model = trainSVM(linkLikes, users, links, friendships, userLinkSamples);
		
		System.out.println("Recommending...");
		HashMap<Long, HashSet<Long>> friendLinksToRecommend = getFriendLinksForRecommending(friendships, "svm");
		System.out.println("Got: " + friendLinksToRecommend.size());
		HashMap<Long, HashMap<Long, Double>> friendRecommendations = recommendLinks(model, linkLikes, users, links, friendships, friendLinksToRecommend);
		System.out.println("foo");
		HashMap<Long, HashSet<Long>> nonFriendLinksToRecommend = getNonFriendLinksForRecommending(friendships, "svm");
		System.out.println("non: " + nonFriendLinksToRecommend.size());
		HashMap<Long, HashMap<Long, Double>> nonFriendRecommendations = recommendLinks(model, linkLikes, users, links, friendships, nonFriendLinksToRecommend);
		
		System.out.println("Saving...");
		saveLinkRecommendations(friendRecommendations, nonFriendRecommendations, "svm");
		
		RecommenderUtil.closeSqlConnection();
		
		System.out.println("Done");
	}
	
	public svm_model trainSVM(HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures, 
								HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> userLinkSamples)
	{
		int dataCount = 0;
		for (long userId : userLinkSamples.keySet()) {
			dataCount += userLinkSamples.get(userId).size();
		}
		
		svm_problem prob = new svm_problem();
		prob.y = new double[dataCount];
		prob.l = dataCount;
		prob.x = new svm_node[dataCount][];
		
		svm_parameter param = new svm_parameter();
		param.C = Constants.C;
		param.svm_type = svm_parameter.C_SVC;
		param.kernel_type = svm_parameter.LINEAR;
		param.cache_size = 20000;
		param.eps = 0.001;
		
		int index = 0;
		int count = 0;
		for (long userId : userLinkSamples.keySet()) {
			System.out.println("User: " + ++count);
			HashSet<Long> samples = userLinkSamples.get(userId);
			Set<Long> userFriends;
			if (friendships.containsKey(userId)) {
				userFriends = friendships.get(userId).keySet();
			}
			else {
				userFriends = new HashSet<Long>();
			}
			
			for (long linkId : samples) {
				double[] combined = combineFeatures(userFeatures.get(userId), linkFeatures.get(linkId));
				
				ArrayList<svm_node> nodes = new ArrayList<svm_node>();
				
				for (int x = 0; x < combined.length; x++) {
					svm_node node = new svm_node();
					node.index = x + 1;
					node.value = combined[x];
					
					nodes.add(node);
				}
				
				for (int x = 0; x < userIds.length; x++) {
					if (userIds[x].equals(userId)) {
						svm_node node = new svm_node();
						node.index = combined.length + x + 1;
						node.value = 1;
						
						nodes.add(node);
					}
					else if (userFriends.contains(userIds[x]) && linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userIds[x])) {
						svm_node node = new svm_node();
						node.index = combined.length + userIds.length + x + 1;
						node.value = 1;
						
						nodes.add(node);
					}
				}
				
				for (int x = 0; x < linkIds.length; x++) {
					if (linkIds[x].equals(linkId)) {
						svm_node node = new svm_node();
						node.index = combined.length + userIds.length + userIds.length + x + 1;
						node.value = 1;
						
						nodes.add(node);
						break;
					}
				}
				
				prob.x[index] = new svm_node[nodes.size()];
				for (int x = 0; x < nodes.size(); x++) {
					prob.x[index][x] = nodes.get(x);
				}
				
				if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) {
					prob.y[index] = 1;
				}
				else {
					prob.y[index] = -1;
				}
				
				index++;
			}
		}
		
		System.out.println("Training...");
		svm_model model = svm.svm_train(prob, param);
		System.out.println("Done");
		return model;
	}
	
	public int[] testSVM(svm_model model, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures, HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> testSamples)
	{
		int truePos = 0;
		int falsePos = 0;
		int trueNeg = 0;
		int falseNeg = 0;
		
		for (long userId : testSamples.keySet()) {
			HashSet<Long> samples = testSamples.get(userId);
			//System.out.println("User Id : " + userId + " " + friendships.containsKey(userId));
			Set<Long> userFriends;
			if (friendships.containsKey(userId)) {
				userFriends = friendships.get(userId).keySet();
			}
			else {
				userFriends = new HashSet<Long>();
			}
			
			for (long linkId : samples) {
				double[] features = combineFeatures(userFeatures.get(userId), linkFeatures.get(linkId));
				ArrayList<svm_node> nodeList = new ArrayList<svm_node>();
				
				for (int x = 0; x < features.length; x++) {
					svm_node node = new svm_node();
					node.index = x + 1;
					node.value = features[x];
					
					nodeList.add(node);
				}
				
				for (int x = 0; x < userIds.length; x++) {
					if (userIds[x].equals(userId)) {
						svm_node node = new svm_node();
						node.index = features.length + x + 1;
						node.value = 1;
						
						nodeList.add(node);
					}
					else if (userFriends.contains(userIds[x]) && linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userIds[x])) {
						svm_node node = new svm_node();
						node.index = features.length + userIds.length + x + 1;
						node.value = 1;
						
						nodeList.add(node);
					}
				}
				
				for (int x = 0; x < linkIds.length; x++) {
					if (linkIds[x].equals(linkId)) {
						svm_node node = new svm_node();
						node.index = features.length + userIds.length + userIds.length + x + 1;
						node.value = 1;
						nodeList.add(node);
						break;
					}
				}
				
				svm_node nodes[] = new svm_node[nodeList.size()];
				
				for (int x = 0; x < nodes.length; x++) {
					nodes[x] = nodeList.get(x);
				}
				
				double prediction = svm.svm_predict(model, nodes);
				double[] dbl = new double[1]; 
				svm.svm_predict_values(model, nodes, dbl);
				prediction=dbl[0];
				
				if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) {
					if (prediction > 0) {
						truePos++;
					}
					else {
						falseNeg++;
					}
				}
				else {
					if (prediction > 0) {
						falsePos++;
					}
					else {
						trueNeg++;
					}
				}
			}
		}
		
		return new int[]{truePos, falsePos, trueNeg, falseNeg};
	}
	
	public double[] combineFeatures(Double[] user, Double[] link)
	{
		double[] feature = new double[user.length + link.length];
		
		for (int x = 0; x < user.length; x++) {
			feature[x] = user[x];
		}
		
		for (int x = 0; x < link.length; x++) {
			feature[x + user.length] = link[x];
		}
		
		return feature;
	}
	
	public HashMap<Long, Double> getAveragePrecision(svm_model model, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures, HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> testSamples)
	{
		HashMap<Long, Double> userAP = new HashMap<Long, Double>();
		
		System.out.println("Test size: " + testSamples.size());
		int count = 0;
		for (long userId : testSamples.keySet()) {
			System.out.println("User " + ++count);
			
			HashSet<Long> links = testSamples.get(userId);
			Set<Long> userFriends;
			if (friendships.containsKey(userId)) {
				userFriends = friendships.get(userId).keySet();
			}
			else {
				userFriends = new HashSet<Long>();
			}
			
			ArrayList<Double> scores = new ArrayList<Double>();
			ArrayList<Long> ids = new ArrayList<Long>();
			
			for (long linkId : links) {
				double[] features = combineFeatures(userFeatures.get(userId), linkFeatures.get(linkId));
				ArrayList<svm_node> nodeList = new ArrayList<svm_node>();
				
				for (int x = 0; x < features.length; x++) {
					svm_node node = new svm_node();
					node.index = x + 1;
					node.value = features[x];
					
					nodeList.add(node);
				}
				
				for (int x = 0; x < userIds.length; x++) {
					if (userIds[x].equals(userId)) {
						svm_node node = new svm_node();
						node.index = features.length + x + 1;
						node.value = 1;
						
						nodeList.add(node);
					}
					else if (userFriends.contains(userIds[x]) && linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userIds[x])) {
						svm_node node = new svm_node();
						node.index = features.length + userIds.length + x + 1;
						node.value = 1;
						
						nodeList.add(node);
					}
				}
				
				for (int x = 0; x < linkIds.length; x++) {
					if (linkIds[x].equals(linkId)) {
						svm_node node = new svm_node();
						node.index = features.length + userIds.length + userIds.length + x + 1;
						node.value = 1;
						
						nodeList.add(node);
						break;
					}
				}
				
				svm_node nodes[] = new svm_node[nodeList.size()];
				
				for (int x = 0; x < nodes.length; x++) {
					nodes[x] = nodeList.get(x);
				}
				
				double prediction = svm.svm_predict(model, nodes);
				double[] dbl = new double[1]; 
				svm.svm_predict_values(model, nodes, dbl);
				//System.out.println("Prediction: " + prediction + " Value: " + dbl[0]);
				
				//scores.add(prediction);
				scores.add(dbl[0]);
				ids.add(linkId);
			}
			
			Object[] sorted = RecommenderUtil.sort(scores, ids);
			ArrayList<Double> sortedScores = (ArrayList<Double>)sorted[0];
			ArrayList<Long> sortedIds = (ArrayList<Long>)sorted[1];
			
			ArrayList<Double> precisions = new ArrayList<Double>();
			int pos = 0;
			System.out.println("Testing: " + sortedScores.size());
			
			for (int x = 0; x < sortedScores.size(); x++) {
				long linkId = sortedIds.get(x);
			
				if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) {
					pos++;
					precisions.add((double)pos / (double)(x+1));
					
					System.out.println("Pos: " + pos + " / " + (x+1));
				}
			}
			
			double ap = 0;
			
			if (precisions.size() > 0) {
				for (double p : precisions) {
					ap += p;
				}
				
				ap /= (double)precisions.size();
			}
			
			userAP.put(userId, ap);
		}
		
		return userAP;
	}
	
	public HashMap<Long, HashMap<Long, Double>> recommendLinks(svm_model model, HashMap<Long, HashSet<Long>> linkLikes,
																HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures,
																HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linksToRecommend)
		throws SQLException
	{	
		HashMap<Long, HashMap<Long, Double>> recommendations = new HashMap<Long, HashMap<Long, Double>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		for (long userId :linksToRecommend.keySet()) {
			HashSet<Long> userLinks = linksToRecommend.get(userId);
			Set<Long> userFriends;
			if (friendships.containsKey(userId)) {
				userFriends = friendships.get(userId).keySet();
			}
			else {
				userFriends = new HashSet<Long>();
			}
			
			HashMap<Long, Double> linkValues = new HashMap<Long, Double>();
			recommendations.put(userId, linkValues);
		
			ResultSet result = statement.executeQuery("SELECT max_links FROM trackUserUpdates WHERE uid=" + userId);
			result.next();
			int maxLinks = result.getInt("max_links");
		
			for (long linkId : userLinks) {
				if (!linkFeatures.containsKey(linkId)) {
					continue;
				}
				
				double[] features = combineFeatures(userFeatures.get(userId), linkFeatures.get(linkId));
				ArrayList<svm_node> nodeList = new ArrayList<svm_node>();
				
				for (int x = 0; x < features.length; x++) {
					svm_node node = new svm_node();
					node.index = x + 1;
					node.value = features[x];
					
					nodeList.add(node);
				}
				
				for (int x = 0; x < userIds.length; x++) {
					if (userIds[x].equals(userId)) {
						svm_node node = new svm_node();
						node.index = features.length + x + 1;
						node.value = 1;
						
						nodeList.add(node);
					}
					else if (userFriends.contains(userIds[x]) && linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userIds[x])) {
						svm_node node = new svm_node();
						node.index = features.length + userIds.length + x + 1;
						node.value = 1;
						
						nodeList.add(node);
					}
				}
				
				for (int x = 0; x < linkIds.length; x++) {
					if (linkIds[x].equals(linkId)) {
						svm_node node = new svm_node();
						node.index = features.length + userIds.length + userIds.length + x + 1;
						node.value = 1;
						
						nodeList.add(node);
						break;
					}
				}
				
				svm_node nodes[] = new svm_node[nodeList.size()];
				
				for (int x = 0; x < nodes.length; x++) {
					nodes[x] = nodeList.get(x);
				}
				
				double[] dbl = new double[1]; 
				svm.svm_predict_values(model, nodes, dbl);
				double prediction = dbl[0];
				
				System.out.println("Prediction: " + prediction + " ID: " + userId);
		
				//We recommend only a set number of links per day/run. 
				//If the recommended links are more than the max number, recommend only the highest scoring links.
				if (linkValues.size() < maxLinks) {
					linkValues.put(linkId, prediction);
				}
				else {
					//Get the lowest scoring recommended link and replace it with the current link
					//if this one has a better score.
					long lowestKey = 0;
					double lowestValue = Double.MAX_VALUE;
		
					for (long id : linkValues.keySet()) {
						if (linkValues.get(id) < lowestValue) {
							lowestKey = id;
							lowestValue = linkValues.get(id);
						}
					}
		
					if (prediction > lowestValue) {
						linkValues.remove(lowestKey);
						linkValues.put(linkId, prediction);
					}
				}
			}
		}
		
		return recommendations;
	}
}