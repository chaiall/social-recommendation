package recommendation;

import java.util.*;

import org.nicta.lr.util.*;

public class Interaction {

	private Map<Long,InteractionMessageHolder> _interactions = null;
	
	public Interaction() {
		_interactions = new HashMap<Long,InteractionMessageHolder>();
	}
	
	/** Maintain interactions between
	 * 
	 * @param uid1 = uid
	 * @param uid2 = from_id
	 */		
	public void addInteraction(long uid1, long uid2, EDirectionType dir, String message) {						
		if (dir == EDirectionType.INCOMING) {
			InteractionMessageHolder messageHolder = _interactions.get(uid1);			
			if (messageHolder == null) {
				messageHolder = new InteractionMessageHolder();				
				_interactions.put(uid1, messageHolder);
			}
			messageHolder.add(uid2,message);
		}
		
		if (dir == EDirectionType.OUTGOING) {
			InteractionMessageHolder messageHolder = _interactions.get(uid2);
			if (messageHolder == null) {
				messageHolder = new InteractionMessageHolder();				
				_interactions.put(uid2, messageHolder);
			}
			messageHolder.add(uid1,message);
		}
	}
	
	public Set<Long> getInteractions(Long uid) {
		return _interactions.get(uid).getInteractees();
	}
	
	public ArrayList<String> getMessages(Long uid){
		return _interactions.get(uid).getMessages();
	}
	
	public Map<Long,InteractionMessageHolder> getAllInteractions() {
		return _interactions;
	}
	
	/*public void addAllInteractions(Interaction i) {
		for (Map.Entry<Long,InteractionMessageHolder> e : i._interactions.entrySet()) { // i's keyset
			long uid = e.getKey();
			Set<Long> to_add = e.getValue().getInteractees();
			if (to_add == null || to_add.size() == 0)
				continue;		
			InteractionMessageHolder messageHolder = this._interactions.get(uid);			
			if (messageHolder == null) {
				messageHolder = new InteractionMessageHolder();				
				this._interactions.put(uid, messageHolder);
			}
			Set<Long> interactions = messageHolder.getInteractees(); // this's keys
			interactions.addAll(to_add);
		}
	}
	
	public void removeAllInteractions(Interaction i) {
		for (Map.Entry<Long,Set<Long>> e : i._interactions.entrySet()) { // i's keyset
			long uid = e.getKey();
			Set<Long> to_remove = e.getValue();
			if (to_remove == null || to_remove.size() == 0)
				continue;			
			Set<Long> interactions = this._interactions.get(uid); // this's keys
			if (interactions == null || interactions.size() == 0) 
				continue;
			interactions.removeAll(to_remove);
		}
	}
	
	public void retainAllInteractions(Interaction i) {
		Set<Long> EMPTY_SET = new HashSet<Long>();
		for (Map.Entry<Long,Set<Long>> e : i._interactions.entrySet()) { // i's keyset
			long uid = e.getKey();
			Set<Long> to_retain = e.getValue();
			if (to_retain == null) { 
				// Nothing to retain here
				to_retain = EMPTY_SET;
			}
			Set<Long> interactions = this._interactions.get(uid); // this's keys
			if (interactions == null || interactions.size() == 0) 
				continue;
			interactions.retainAll(to_retain);
		}
	}*/
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Interaction i = new Interaction();
		//Direction dir = Direction.BIDIRECTIONAL;
		//Direction dir = Direction.INCOMING;
		/*EDirectionType dir = EDirectionType.OUTGOING;
		i.addInteraction(1, 2, dir);
		i.addInteraction(3, 4, dir);
		i.addInteraction(1, 3, dir);
		System.out.println("1: " + i.getInteractions(1l));
		System.out.println("2: " + i.getInteractions(2l));
		System.out.println("3: " + i.getInteractions(3l));
		System.out.println("4: " + i.getInteractions(4l));
		System.out.println("5: " + i.getInteractions(5l));*/
	}

}