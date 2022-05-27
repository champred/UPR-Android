package ly.mens.rndpkmn

//https://gist.github.com/sagar-viradiya/891cf7d08b6ac13bb1fbdc411b76f6a5
class Trie {

	data class Node(var word: String? = null, val childNodes: MutableMap<Char, Node> = mutableMapOf()) {
		fun children(words: MutableList<String> = mutableListOf()): List<String> {
			word?.let { words.add(it) }
			for (n in childNodes.values) {
				if (words.size >= 3) break
				n.children(words)
			}
			return words
		}
	}

	private val root = Node()

	fun insert(word: String) {
		var currentNode = root
		for (char in word) {
			if (currentNode.childNodes[char] == null) {
				currentNode.childNodes[char] = Node()
			}
			currentNode = currentNode.childNodes[char]!!
		}
		currentNode.word = word
	}

	operator fun contains(word: String): Boolean {
		var currentNode = root
		for (char in word) {
			if (currentNode.childNodes[char] == null) {
				return false
			}
			currentNode = currentNode.childNodes[char]!!
		}
		return currentNode.word != null
	}

	operator fun get(word: String): Node? {
		var currentNode = root
		for (char in word) {
			if (currentNode.childNodes[char] == null) {
				return null
			}
			currentNode = currentNode.childNodes[char]!!
		}
		return currentNode
	}

	fun startsWith(word: String): Boolean {
		var currentNode = root
		for (char in word) {
			if (currentNode.childNodes[char] == null) {
				return false
			}
			currentNode = currentNode.childNodes[char]!!
		}
		return currentNode.word == null
	}

}
