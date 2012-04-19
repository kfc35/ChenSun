package server;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import shared.ProjectConfig;
import shared.Utils;

import database.DBManager;
import database.DatabaseAdmin;

public class SocialNetworkAdmin {
	private static final boolean DEBUG = ProjectConfig.DEBUG;
	
	public static String friendReqNotification(Connection conn, String username) {
		String command = "";
		int requestCount = DatabaseAdmin.getFriendReqCount(conn, username);
		if (requestCount != 0) {
			command = "print Pending Friend Requests (" + requestCount
				+ ") [To view: friendRequests];";
		}
		return command;
	}
	
	public static String regReqNotification(Connection conn, String username) {
		String command = "";
		int requestCount = DatabaseAdmin.getRegReqCount(conn, username);
		if (requestCount != 0) {
			command = "print Pending User Registration Requests ("
					+ requestCount + ") [To view: regRequests];";
		}
		return command;
	}
	
	public static String adminReqNotification(Connection conn, String username) {
		String command = "";
		int requestCount = DatabaseAdmin.getAdminReqeustCount(conn, username);
		if (requestCount > 0) {
			command = "print Pending Board Admin Requests (" + 
					requestCount + ") [To view: adminRequests];";
		}
		return command;
	}
	
	public static String insertRegRequest(Connection conn, String newUser, int aid, String pwdStore) {
		String command = "";
		int success = DatabaseAdmin.insertRegRequest(conn, newUser, aid, pwdStore);
		if (success == 1) {
			command = "print Registration request for " + newUser
					+ " has been sent.;print Once an admin from your group "
					+ "approves, you will be added to the system.;print ;";
		} else if (success == -1) {
			command = "print User already pending registration approval. Try again with a different username.;print ;";
		} else {
			command = "print Registration failed due to database error. " +
					"Please try again or contact System Admin.;print ;";
		}
		return command;
	}
	
	public static String regRequests(Connection conn, String username) {
		String command = "";
		List<String> pendingUsers = DatabaseAdmin.getRegRequestList(conn, username);
		if (pendingUsers == null) {
			command = "print Database error. Please contact System Admin.;";
		} else if (pendingUsers.size() == 0) {
			command = "print No pending registration requests at the moment.;";
		} else {
			command = "print Pending User Registration Requests ("
					+ pendingUsers.size() + "):;";
			for (String u: pendingUsers) {
				command = command + "print " + u + ";";
			}
			command += "print ;print [To approve: approve "
					+ "<username1>, <username2>];print [To remove: "
					+ "remove <username1>, <username2>];askForInput;";
		}
		return command;
	}
	
	public static String friendRequests(Connection conn, String username) {
		String command = "";
		List<String> pendingFriends = DatabaseAdmin.getFriendRequestList(conn, username);
		if (pendingFriends == null) {
			command = "print Database error. Please contact System Admin.;";
		} else if (pendingFriends.size() == 0) {
			command = "print No pending friend requests at the moment.;";
		} else {
			command = "print Pending Friend Requests (" + pendingFriends.size() + "):;";
			for (String f: pendingFriends) {
				command = command + "print " + f + ";";
			}
			command += ";print ;print [To approve: approve "
					+ "<username1>, <username2>];print [To remove: "
					+ "remove <username1>, <username2>];askForInput;";
		}
		return command;
	}

	public static String regApprove(Connection conn, String username) {
		String success = "print "+username+" has been added to the system.;";
		String error = "print Database error occurred while approving registration for " + 
				username + ". Please try again or contact the System Admin.;";
		String[] userInfo = DatabaseAdmin.getRegUserInfo(conn, username);
		if (userInfo == null) {
			if (DEBUG) System.err.println("regApprove: userInfo returned null");
			return error;
		}
		String pwhash = userInfo[1];
		int aid = Integer.parseInt(userInfo[2]);
		try {
			conn.setAutoCommit(false);
		} catch (SQLException e) {
			if (DEBUG) System.err.println("regApprove: turning off auto commit failed.");
			return error;
		}
		
		int deleteStatus = DatabaseAdmin.deleteRegRequest(conn, username);
		int addStatus = DatabaseAdmin.addUser(conn, username, pwhash, aid);
		System.out.println("Going into addFriendsFromGroup");
		int addFriendStatus = DatabaseAdmin.addFriendsFromGroup(conn, username, aid);
		
		if (deleteStatus != 1 || addStatus != 1 || addFriendStatus <= 0) {
			DBManager.rollback(conn);
			DBManager.trueAutoCommit(conn);
			if (DEBUG) System.err.printf("regApprove: DB operations failed. " +
					"deleteStatus: %d, addStatus: %d, addFriendStatus: %d\n", deleteStatus, addStatus, addFriendStatus);
			return error;
		} else {
			try {
				conn.commit();
				DBManager.trueAutoCommit(conn);
				return success;
			} catch (SQLException e) {
				DBManager.trueAutoCommit(conn);
				if (DEBUG) e.printStackTrace();
				return error;
			}
		}
	}
	
	public static String friendApprove(Connection conn, String requester, String requestee) {
		String success = "print "+requester+" has been added as your friend.;";
		String error = "print Database error occurred while friending " + 
				requester + ". Please try again or contact the System Admin.;";
		try {
			conn.setAutoCommit(false);
		} catch (SQLException e) {
			if (DEBUG) System.err.println("friendApprove: turning off auto commit failed.");
			return error;
		}
		
		int deleteStatus = DatabaseAdmin.deleteFriendRequest(conn, requester, requestee);
		int addStatus = DatabaseAdmin.addFriend(conn, requester, requestee);
		
		if (deleteStatus != 1 || addStatus != 1) {
			DBManager.rollback(conn);
			DBManager.trueAutoCommit(conn);
			if (DEBUG) System.err.printf("friendApprove: DB operations failed. " +
					"deleteStatus: %d, addStatus: %d\n", deleteStatus, addStatus);
			return error;
		} else {
			try {
				conn.commit();
				DBManager.trueAutoCommit(conn);
				return success;
			} catch (SQLException e) {
				DBManager.trueAutoCommit(conn);
				if (DEBUG) e.printStackTrace();
				return error;
			}
		}
	}

	public static String regRemove(Connection conn, String username) {
		String success = "print "+username+" has been deleted from the system.;";
		String error = "print Database error occurred while removing registration for " + 
				username + ". Please try again or contact the System Admin.;";
		int status = DatabaseAdmin.deleteRegRequest(conn, username);
		if (status == 1) {
			return success;
		} else {
			return error;
		}
	}
	
	public static String friendReqRemove(Connection conn, String requester, String requestee) {
		String success = "print Friend request from " + requester + " has been deleted.;";
		String error = "print Database error occurred while removing friend request from " + 
				requester + ". Please try again or contact the System Admin.;";
		int status = DatabaseAdmin.deleteFriendRequest(conn, requester, requestee);
		if (status == 1) {
			return success;
		} else {
			return error;
		}
	}

	public static String displayFriendableUsers(Connection conn, String prefix, List<String[]> friendableUsers) {
		String command;
		if (prefix == "") {
			command = "print Users in the system:;";
		} else {
			command = "print Usernames starting with '" + prefix + "';";
		}
		for (String[] userInfo : friendableUsers) {
			prefix = prefix.toLowerCase();
			if (userInfo[0].toLowerCase().startsWith(prefix)) {
				command += "print " + userInfo[0] + " (" + userInfo[1]
						+ ");";
			}
		}
		command += "print ;print Type the name of the user you wish to friend:;"
				+ "askForInput;";
		return command;
	}
	
	public static String displayGroupList(Connection conn, Map<Integer, String> groupList, String newUser) {
		Iterator<Entry<Integer, String>> it = groupList.entrySet().iterator();
		String list = "";
		while (it.hasNext()) {
	        Map.Entry<Integer, String> pairs = (Map.Entry<Integer, String>)it.next();
	        list += "print " + pairs.getKey() + " " + pairs.getValue() + ";";
	    }
		return "print Choose a cappella group for " + newUser
				+ " by entering the group number:;" + list + "askForInput;";
	}
	
	public static String insertFriendRequest(Connection conn, String requestee, String requester) {
		String command = "";
		int status = DatabaseAdmin.insertFriendRequest(conn, requestee, requester);
		if (status == 1) {
			command = "print Friend request sent to " + requestee + ".;";
		} else {
			command = "print Database Error while sending friend request. Please try again or contact the System Admin.;";
		}
		return command;
	}
	
	public static String displayDeletableUsers(List<String[]> users) {
		String command = "print Users in your A Cappella group that you can delete:;";
		for (String[] u: users) {
			command += "print " + u[0] + ";";
		}
		command += "print ;print Type the name of the user you wish to delete:;askForInput;";
		return command;
	}
	
	public static String deleteUser(Connection conn, String username) {
		String success = "print " + username + " has been deleted from the system.;";
		String error = "print Database Error while deleting " + username + ". Please try again or contact the System Admin.;";
		int status = DatabaseAdmin.deleteUser(conn, username);
		if (status == 1) {
			return success;
		} else {
			return error;
		}
	}
	
	public static String showFriends(Connection conn, String username) {
		String command = "print Your Friends:;";
		List<String> friends = DatabaseAdmin.getFriends(conn, username);
		if (friends == null) {
			command = "print Database Error while getting friend list. Please try again or contact the System Admin.;";
		} else if (friends.size() == 0) {
			command = "print You have no friends right now.;print To add a friend: type addFriend;";
		} else {
			for (String f: friends) {
				String[] userInfo = DatabaseAdmin.getUserInfo(conn, f);
				command += "print " + userInfo[0] + " (" + userInfo[2] + 
						"-" + userInfo[3].toUpperCase() + ");";
				// April (Fantasia-MEMBER)
			}
		}
		return command;
	}
	
	public static String displayRoleChange(List<String[]> users) {
		String command = "print Users in your A Cappella group that you can change roles for:;";
		for (String[] u: users) {
			command += "print " + u[0] + " (" + u[1].toUpperCase() + ");";
		}
		command += "print ;print Type the name of the user you wish to change role for:;askForInput;";
		return command;
	}
	
	public static String changeRole(Connection conn, String username, String role) {
		int status = DatabaseAdmin.changeRole(conn, username, role);
		if (status == 1) {
			String from;
			if (role.equals("admin")) {
				from = "MEMBER";
			} else {
				from = "ADMIN";
			}
			return "print Role for " + username + " has been changed from " + from + 
					" to " + role.toUpperCase() + ";";
		} else {
			return "print Database Error while changing role for " + username + 
					". Please try again or contact the System Admin.;";
		}
	}
	
	public static String displaySATransferableUsers(List<String> admins) {
		String command = "print Users in your A Cappella group that you can transfer SA role to:;";
		for (String a: admins) {
			command += "print " + a + " (ADMIN);";
		}
		command += "print ;print Type the name of the user you wish to transfer SA role to:;"
				+ "askForInput;";
		return command;
	}
	
	public static String transferSA(Connection conn, String from, String to) {
		String success = "print SA role has been transferred to " + to + ";";
		String error = "print Database error occurred while transferring SA role to " + 
				to + ". Please try again or contact the System Admin.;";
		try {
			conn.setAutoCommit(false);
		} catch (SQLException e) {
			if (DEBUG) System.err.println("transferSA: turning off auto commit failed.");
			return error;
		}
		
		int demoteStatus = DatabaseAdmin.changeRole(conn, from, "admin");
		int promoteStatus = DatabaseAdmin.changeRole(conn, to, "sa");
		
		if (demoteStatus != 1 || promoteStatus != 1) {
			DBManager.rollback(conn);
			DBManager.trueAutoCommit(conn);
			if (DEBUG) System.err.printf("transferSA: DB operations failed. " +
					"demoteStatus: %d, promoteStatus: %d\n", demoteStatus, promoteStatus);
			return error;
		} else {
			try {
				conn.commit();
				DBManager.trueAutoCommit(conn);
				return success;
			} catch (SQLException e) {
				DBManager.trueAutoCommit(conn);
				if (DEBUG) e.printStackTrace();
				return error;
			}
		}
	}
	
	private static String printUserInfo(String username, String role, String aname) {
		String command = "print Logged in as: " + username + ";";
		command += "print Role: " + role.toUpperCase() + ";";
		command += "print A Cappella Group: " + aname + ";print ;";
		return command;
	}
	
	public static String printUserInfo(Connection conn, String username) {
		String[] userInfo = DatabaseAdmin.getUserInfo(conn, username);
		String command = printUserInfo(userInfo[0], userInfo[3], userInfo[2]);
		command += friendReqNotification(conn, username);
		if (userInfo[3].equals("sa") || userInfo[3].equals("admin")) {
			command += regReqNotification(conn, username);
			command += adminReqNotification(conn, username);
		}
		command += Utils.getHR(80) + "print ;";
		return command;
	}
	
	public static String printUserInfo(String username) {
		Connection conn = DBManager.getConnection();
		String command = printUserInfo(conn, username);
		DBManager.closeConnection(conn);
		return command;
	}
	
	/**
	 * Precond: if board is freeforall, region is post, if not, board and regions are valid
	 * @param conn
	 * @param board
	 * @param region
	 * @return
	 */
	public static String displayParticipAndAdmins(Connection conn, String board, String region) {
		String command = "print Displaying participants in "+board+"/"+region+":;";
		List<String> admins = DatabaseAdmin.getAdminsOfBoard(conn, board);
		for (String a: admins) {
			command += "print " + a + " (Admin);";
		}
		List<String[]> part = DatabaseAdmin.getParticipants(conn, board, region);
		for (String[] p: part) {
			command += "print " + p[0];
			if (p[1].equals("view")) {
				command += " (view only)";
			}
			command += ";";
		}
		//command += "print ;print Other Commands: addParticipants, removeParticipants, editParticipants;";
		return command;
	}
	
	public static List<String> getAddableParticip(Connection conn, String username, String board, String region) {
		List<String> addables = new ArrayList<String>();
		// friends that are not already participants of the region and not admins
		List<String> friends = DatabaseAdmin.getFriends(conn, username);
		List<String[]> participants = DatabaseAdmin.getParticipants(conn, board, region);
		for (String f: friends) {
			if (board == "freeforall" || !DatabaseAdmin.isAdmin(conn, f)) {
				boolean isPart = false;
				for (String[] p: participants) {
					if (f.equals(p[0])) {
						isPart = true;
						break;
					}
				}
				if (!isPart) {
					addables.add(f);
				}
			}
		}
		return addables;
	}
	
	public static String displayAddableParticip(List<String> addables, String board) {
		String command = "";
		if (!board.equals("freeforall")) {
			command = "print To add an admin, use the 'addAdmin' command. Admins " +
					"are added to the entire board and has to be approved by all other " +
					"admins of the board.;print ;";
		}
		command += "print Friends you can add as a participant to this region/post.;";
		for (String a: addables) {
			command += "print " + a + ";";
		}
		command += "print To add participants with view and post privilege, " +
				"type 'viewpost <user1>, <user2>';" +
				"print To add participants with view only privilege, " +
				"type 'view <user1>, <user2>';askForInput;";
		return command;
	}
	
	public static String displayRemoveParticip(Connection conn, String board, String region) {
		String command = "print Displaying participants in "+board+"/"+region+":;";
		List<String[]> part = DatabaseAdmin.getParticipants(conn, board, region);
		for (String[] p: part) {
			command += "print " + p[0];
			if (p[1].equals("view")) {
				command += " (view only)";
			}
			command += ";";
		}
		command += "print To remove participants, type their usernames separated " +
				"by comma: '<user1>, <user2>';askForInput;";
		return command;
	}
	
	public static String displayEditableParticip(List<String[]> editables) {
		String command = "print Participants you can edit in this region/post.;";
		for (String[] e: editables) {
			command += "print " + e[0];
			if (e[1].equals("view")) {
				command += " (view only)";
			} else if (e[1].equals("viewpost")) {
				command += " (view and post)";
			}
			command += ";";
		}
		command += "print Type the name of the user you wish to change permission for:;" +
				"askForInput;";
		return command;
	}
	
	public static String removeParticipant(Connection conn, String board, String region, String username) {
		String success = "print " + username + " has been removed from " + 
				board + "/" + region + ";";
		String error = "print Database Error while removing " + username + ". Please " +
				"try again or contact the System Admin.;";
		int status = DatabaseAdmin.removeParticipant(conn, board, region, username);
		if (status == 1) {
			return success;
		} else {
			return error;
		}
	}
	
	public static String editParticipant(Connection conn, String board, String region, String username, String priv) {
		String success = "print permission for username has been changed.;";
		String error = "print Database Error while changing permission for "+username+". " +
				"Please try again or contact the System Admin.;";
		int status = DatabaseAdmin.editParticipant(conn, board, region, username, priv);
		if (status == 1) {
			return success;
		} else {
			return error;
		}
	}
	
	public static String displayAddableAdmins(List<String> addables) {
		String command = "print Admins you are friends with:;";
		for (String a: addables) {
			command += "print " + a + ";";
		}
		command += "print Type the username of the admin you want to add:;" +
				"askForInput;";
		return command;
	}
	
	public static String addAdminRequest(Connection conn, String board, String username) {
		String success = "print Request for " + username + " to be added to " + board +
				"has been processed.;print Once all the board admins approve, he/she " +
				"will be added to the board.;";
		String error = "print Database Error while processing add admin request for " + 
				username + ". Please try again or contact a System Admin.;";
		int status = DatabaseAdmin.addAdminRequest(conn, board, username);
		if (status == 1) {
			return success;
		} else {
			return error;
		}
	}
	
	public static String displayAdminRequests(List<String[]> requests) {
		String command = "";
		if (requests == null) {
			command = "print Database error. Please contact System Admin.;";
		} else if (requests.size() == 0) {
			command = "print No pending admin requests at the time.;";
		} else {
			command = "print Admins waiting approval to be added to boards:;";
			for (String[] r: requests) {
				command += "print " + r[0] + ". " + r[1] + " - " + r[2] + ";";
			}
			command += "print To approve requests, use the request number:" +
					" 'approve ###, ###';";
			command += "print To remove requests, type 'remove ###, ###';";
			command += "askForInput;";
		}
		return command;
	}
	
	public static String approveAdmin(Connection conn, String board, String username) {
		String command = "";
		String error = "print Database Error while adding " + username + " as an admin " +
				"to " + board + ". Please try again or contact a System Admin.;";
		String success = "print " + username + " has been added as an admin to " +
				board + ".;";
		try {
			conn.setAutoCommit(false);
			// add approval to addadminapproval table
			// query number of admin for the board
			// if num of entry in approval match num of admin
				// remove admin request
				// add admin to board
			
			// for a request to show up, there has to be an entry in request for a board
			// the user belongs to and the user hasn't approved that entry yet.
			// SELECT bname, username FROM pendingadmins WHERE bname IN (
			// SELECT bname FROM boardadmins WHERE username = ?)
			// SELECT bname, admin FROM addadminapprovals WHERE 
			int deleteStatus = DatabaseAdmin.removeAdminRequest(conn, board, username);
			int addStatus = DatabaseAdmin.addAdminToBoard(conn, board, username);
			if (deleteStatus != 1 || addStatus != 1) {
				conn.rollback();
				command = error;
			} else {
				conn.commit();
				command = success;
			}
		} catch (SQLException e) {
			command = error;
		} finally {
			DBManager.trueAutoCommit(conn);
		}
		return command;
	}
	
	public static String removeAdmin(Connection conn, String board, String username) {
		String success = "print Admin Request of " + username + " for " + board + 
				" has been removed.;";
		String error = "print Database Error while removing admin request of " + 
				username + " for " + board + ";";
		int status = DatabaseAdmin.removeAdminRequest(conn, board, username);
		if (status == 1) {
			return success;
		} else {
			return error;
		}
	}
	
	public static String approveAdminReqs(Connection conn, String[] indices, List<String[]> requests) {
		String command = "";
		for (String i: indices) {
			try {
				int index = Integer.parseInt(i);
				if (index >= requests.size() || index < 0) {
					throw new NumberFormatException();
				}
				String[] request = requests.get(index);
				command += approveAdmin(conn, request[1], request[2]);
			} catch (NumberFormatException e) {
				command = "print Wrong format.;" + ServerInputProcessor.CANCEL;
				break;
			}
		}
		return command;
	}
	
	public static String removeAdminReqs(Connection conn, String[] indices, List<String[]> requests) {
		String command = "";
		for (String i: indices) {
			try {
				int index = Integer.parseInt(i);
				if (index >= requests.size() || index < 0) {
					throw new NumberFormatException();
				}
				String[] request = requests.get(index);
				command += removeAdmin(conn, request[1], request[2]);
			} catch (NumberFormatException e) {
				command = "print Wrong format.;" + ServerInputProcessor.CANCEL;
				break;
			}
		}
		return command;
	}
}







