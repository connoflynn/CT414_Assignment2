import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.io.StringReader;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.Scanner;

public class MapReduceAssignment {

  public static void main(String[] args) {

    if (args.length < 3) {
      System.err.println("usage: java MapReduceFiles file1.txt file2.txt file3.txt");

    }

    Map<String, String> input = new HashMap<String, String>();
    try {
      input.put(args[0], readFile(args[0]));
      input.put(args[1], readFile(args[1]));
      input.put(args[2], readFile(args[2]));
    }
    catch (IOException ex)
    {
        System.err.println("Error reading files...\n" + ex.getMessage());
        ex.printStackTrace();
        System.exit(0);
    }

    //Distributed MapReduce
    {
      final Map<String, Map<String, Integer>> output = new HashMap<String, Map<String, Integer>>();

      long mapTime;
      long groupTime;
      long reduceTime;

      // MAP:
      final List<MappedItem> mappedItems = new LinkedList<MappedItem>();

      final MapCallback<String, MappedItem> mapCallback = new MapCallback<String, MappedItem>() {
        @Override
        public synchronized void mapDone(String file, List<MappedItem> results) {
          mappedItems.addAll(results);
        }
      };

      List<Thread> mapCluster = new ArrayList<Thread>();

      int linesPerThread = 1000;

      // A map that will be a key of the file name and value of a list of strings that are 
      // split by the number of lines specified
      Map<String, List<String>> splitInput = new HashMap<String, List<String>>();
      for (String i : input.keySet()) {
        List<String> lines = splitStrings(input.get(i), linesPerThread);
        splitInput.put(i, lines);
      }
      
      //Measure time of mapping phase
      long beforeMapTime = System.currentTimeMillis();

      Iterator<Map.Entry<String, List<String>>> inputIter = splitInput.entrySet().iterator();
      while(inputIter.hasNext()) {
        Map.Entry<String, List<String>> entry = inputIter.next();

        for(int i=0;i<entry.getValue().size();i++){
          final String file = entry.getKey();
          final String contents = entry.getValue().get(i);

          Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
              map(file, contents, mapCallback);
            }
          });
          mapCluster.add(t);
          t.start();
        }
      }
      System.out.println("Number of Map Phase Threads created: " + mapCluster.size());

      // wait for mapping phase to be over:
      for(Thread t : mapCluster) {
        try {
          t.join();
        } catch(InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      long afterMapTime = System.currentTimeMillis();
      mapTime = afterMapTime - beforeMapTime;

      // GROUP:

      long beforeGroupTime = System.currentTimeMillis();
      Map<String, List<String>> groupedItems = new HashMap<String, List<String>>();

      Iterator<MappedItem> mappedIter = mappedItems.iterator();
      while(mappedIter.hasNext()) {
        MappedItem item = mappedIter.next();
        String word = item.getWord();
        String file = item.getFile();
        List<String> list = groupedItems.get(word);
        if (list == null) {
          list = new LinkedList<String>();
          groupedItems.put(word, list);
        }
        list.add(file);
      }
      long afterGroupTime = System.currentTimeMillis();
      groupTime = afterGroupTime - beforeGroupTime;

      // REDUCE:

      final ReduceCallback<String, String, Integer> reduceCallback = new ReduceCallback<String, String, Integer>() {
        @Override
        public synchronized void reduceDone(String k, Map<String, Integer> v) {
          output.put(k, v);
        }
      };

      int sizeOfGroups = 3;

      Iterator<Map.Entry<String, List<String>>> groupedIter = groupedItems.entrySet().iterator();

      //groupOfGroups is a list containing lists of lists of size "sizeOfGroups" of map entries
      //groupOfGroups will be used to create one thread per list item
      List<List<Map.Entry<String, List<String>>>> groupOfGroups= new ArrayList<>();
      //create a list that will store map entrys and will be the size of the groups
      List<Map.Entry<String, List<String>>> temp= new ArrayList<>();
      int count =0;
      //iterate the grouped items and add them to a new list of lists
      while(groupedIter.hasNext()) {
        Map.Entry<String, List<String>> entry = groupedIter.next();
        temp.add(entry);
        count++;
        //if the size is the size given
        if(count%sizeOfGroups == 0){
          groupOfGroups.add(new ArrayList<>(temp));
          temp.clear();
        }
      }
      if(!temp.isEmpty()){
        groupOfGroups.add(temp);
      }

      List<Thread> reduceCluster = new ArrayList<Thread>();

      long beforeReduceTime = System.currentTimeMillis();
      Iterator<List<Map.Entry<String, List<String>>>> groupOfGroupIter = groupOfGroups.iterator();
      while(groupOfGroupIter.hasNext()) {
        List<Map.Entry<String, List<String>>> entrys = groupOfGroupIter.next();

        Thread t = new Thread(new Runnable() {
          @Override
          public void run() {
            for(int i =0; i<entrys.size();i++){
              final String word = entrys.get(i).getKey();
              final List<String> list = entrys.get(i).getValue();
              reduce(word, list, reduceCallback);
            }
          }
        });
        reduceCluster.add(t);
        t.start();
      }

      // wait for reducing phase to be over:
      for(Thread t : reduceCluster) {
        try {
          t.join();
        } catch(InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
      long afterReduceTime = System.currentTimeMillis();
      reduceTime = afterReduceTime - beforeReduceTime;

      System.out.println(output);
      System.out.println("Number of invividual words found: " + output.size());
      System.out.println("Time taken for map phase: " + mapTime + "ms");
      System.out.println("Time taken for group phase: " + groupTime + "ms");
      System.out.println("Time taken for reduce phase: " + reduceTime + "ms");
    }
  }

  public static interface MapCallback<E, V> {

    public void mapDone(E key, List<V> values);
  }

  public static void map(String file, String contents, MapCallback<String, MappedItem> callback) {
    String[] words = contents.trim().split("\\s+");
    List<MappedItem> results = new ArrayList<MappedItem>(words.length);
    for(String word: words) {
      results.add(new MappedItem(word, file));
    }
    callback.mapDone(file, results);
  }

  public static interface ReduceCallback<E, K, V> {

    public void reduceDone(E e, Map<K,V> results);
  }

  public static void reduce(String word, List<String> list, ReduceCallback<String, String, Integer> callback) {

    Map<String, Integer> reducedList = new HashMap<String, Integer>();
    for(String file: list) {
      Integer occurrences = reducedList.get(file);
      if (occurrences == null) {
        reducedList.put(file, 1);
      } else {
        reducedList.put(file, occurrences.intValue() + 1);
      }
    }
    callback.reduceDone(word, reducedList);
  }

  private static class MappedItem {

    private final String word;
    private final String file;

    public MappedItem(String word, String file) {
      this.word = cleanWord(word);
      this.file = file;
    }

    public String getWord() {
      return word;
    }

    public String getFile() {
      return file;
    }

    @Override
    public String toString() {
      return "[\"" + word + "\",\"" + file + "\"]";
    }

    //Clean the word (Change to lowercase and remove any punctuations from the word)
    public String cleanWord(String word){

      word = word.toLowerCase();

      word = word.replaceAll("[.,?!:;*£$&(){}@/`_+=-]", "");
      word = word.replace("\"", "");
      word = word.replace("'", "");

      return word;
    }
  }

  private static String readFile(String pathname) throws IOException {
    File file = new File(pathname);
    StringBuilder fileContents = new StringBuilder((int) file.length());
    Scanner scanner = new Scanner(new BufferedReader(new FileReader(file)));
    String lineSeparator = System.getProperty("line.separator");

    try {
      if (scanner.hasNextLine()) {
        fileContents.append(scanner.nextLine());
      }
      while (scanner.hasNextLine()) {
        fileContents.append(lineSeparator + scanner.nextLine());
      }
      return fileContents.toString();
    } finally {
      scanner.close();
    }
  }

  //A fuction to split a string into an arraylist of strings by the number of lines specified 
  private static List<String> splitStrings(String input, int linesPerSplit){
    List<String> output = new ArrayList<>();

    BufferedReader br = new BufferedReader(new StringReader(input));
    String line;
    String linesSection = "";
    int count = 0;
    try {
      while((line = br.readLine()) != null){
        linesSection += line + "\n";
        count += 1;
        if(count%linesPerSplit == 0){
          output.add(linesSection);
          linesSection = "";
        }
      }
      if(!linesSection.equals("")){
        output.add(linesSection);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return output;
  }
}
