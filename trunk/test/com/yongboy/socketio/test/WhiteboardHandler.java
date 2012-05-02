package com.yongboy.socketio.test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.log4j.Logger;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yongboy.socketio.server.IOHandlerAbs;
import com.yongboy.socketio.server.transport.GenericIO;
import com.yongboy.socketio.server.transport.IOClient;

/**
 * 
 * @author nieyong
 * @time 2012-4-27
 * @version 1.0
 */
public class WhiteboardHandler extends IOHandlerAbs {
	private Logger log = Logger.getLogger(this.getClass());
	// 房间的一对多<房间号,List<客户端>>
	private ConcurrentMap<String, Set<IOClient>> roomClients = new ConcurrentHashMap<String, Set<IOClient>>();

	@Override
	public void OnConnect(IOClient client) {
		log.debug("A user connected :: " + client.getSessionID());
	}

	@Override
	public void OnDisconnect(IOClient client) {
		log.debug("A user disconnected :: " + client.getSessionID()
				+ " :: hope it was fun");

		GenericIO genericIO = (GenericIO) client;
		Object roomObj = genericIO.attr.get("room");

		if (roomObj == null)
			return;

		String roomId = roomObj.toString();

		Set<IOClient> clients = roomClients.get(roomId);
		clients.remove(client);

		int clientNums = clients.size();

		// 通知其它客户端，有人离线
		broadcastRoom(roomId, client, "roomCount", String.format(
				"{\"room\":\"%s\",\"num\":%s}", roomId, clientNums));
	}

	@Override
	public void OnMessage(IOClient client, String oriMessage) {
		log.debug("Got a message :: " + oriMessage
				+ " :: echoing it back to :: " + client.getSessionID());
		String jsonString = oriMessage.substring(oriMessage.indexOf('{'));
		jsonString = jsonString.replaceAll("\\\\", "");

		log.debug("jsonString " + jsonString);

		JSONObject jsonObject = JSON.parseObject(jsonString);
		String eventName = jsonObject.get("name").toString();

		JSONArray argsArray = jsonObject.getJSONArray("args");
		JSONObject obj = argsArray.getJSONObject(0);

		String roomId = obj.getString("room");

		if (eventName.equals("roomNotice")) {
			if (!roomClients.containsKey(roomId)) {
				roomClients.put(roomId, new HashSet<IOClient>());
				GenericIO genericIO = (GenericIO) client;
				genericIO.attr.put("room", roomId);
			}

			roomClients.get(roomId).add(client);

			int clientNums = roomClients.get(roomId).size();
			broadcastRoom(roomId, client, "roomCount", String.format(
					"{\"room\":\"%s\",\"num\":%s}", roomId, clientNums));

			String content = String.format(
					"5:::{\"name\":\"%s\",\"args\":[%s]}", "roomCount", String
							.format("{\"room\":\"%s\",\"num\":%s}", roomId,
									clientNums));
			client.send(content);

			return;
		}

		broadcastRoom(roomId, client, eventName, obj.toJSONString());
	}

	/**
	 * @author nieyong
	 * @time 2012-4-27
	 * 
	 * @param string
	 * @param format
	 */
	private void broadcastRoom(String roomId, IOClient client,
			String eventName, String jsonString) {
		Set<IOClient> clients = roomClients.get(roomId);
		if (clients == null || clients.isEmpty())
			return;

		String content = String.format("5:::{\"name\":\"%s\",\"args\":[%s]}",
				eventName, jsonString);
		for (IOClient rc : clients) {
			if (rc == null || rc.getSessionID().equals(client.getSessionID())) {
				continue;
			}

			rc.send(content);
		}
	}

	@Override
	public void OnShutdown() {
		log.debug("shutdown now ~~~");
	}
}