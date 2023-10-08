

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class KSSU {
    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Write the absolute path of your save file (or just drag it):");
            System.out.println("Example: \"C:\\Users\\user1\\Desktop\\Kirby Super Star Ultra.sav\"");
            System.out.println();
            String input = scanner.nextLine();
            String path = input;
            path = path.replace("\"", "");
            boolean export = false;
            //The save file is read
            byte[] originalFile = Files.readAllBytes(Paths.get(path));
            KSSU saveFile = new KSSU(originalFile);

            //Infinite loop
            while (true) {
                System.out.println();
                System.out.println(
                        """
                                Options:
                                0: Change region
                                1: Copy save slot
                                2: Fix Checksums
                                3: Display save file range
                                4: Read another file
                                5: Exit"""
                );
                input = scanner.nextLine();
                //Change region
                switch (input) {
                    case "0" -> {
                        System.out.println("Select region to convert the save file:\n0: USA/Europe/Korea\n1: Japan");
                        input = scanner.nextLine();
                        if (input.equals("0")) saveFile.changeRegion(true);
                        else if (input.equals("1")) saveFile.changeRegion(false);
                        export = true;
                    }
                    //Copy save slot
                    case "1" -> {
                        System.out.println("Slot to copy (1-3):");
                        int slotToCopy = Integer.parseInt(scanner.nextLine()) - 1;
                        System.out.println("Slot to overwrite (1-3):");
                        int slotToPaste = Integer.parseInt(scanner.nextLine()) - 1;
                        saveFile.copySlot(slotToCopy, slotToPaste);
                        export = true;
                    }
                    //Fix Checksums
                    case "2" -> export = true;

                    //Display save range
                    case "3" -> System.out.println(saveFile.offsetReport());

                    //Read another save file
                    case "4" -> {
                        System.out.println("Write the absolute path of your save file (or just drag it):");
                        System.out.println("Example: \"C:\\Users\\user1\\Desktop\\Kirby Super Star Ultra.sav\"");
                        input = scanner.nextLine();
                        path = input;
                        path = path.replace("\"", "");
                        //The save file is read
                        originalFile = Files.readAllBytes(Paths.get(path));
                        saveFile = new KSSU(originalFile);
                    }

                    //Exit
                    case "5" -> System.exit(1);
                }

                if (export) {
                    System.out.println();
                    byte[] fileBytes = saveFile.getBytes();
                    writeFile(originalFile, path + ".bak");
                    writeFile(fileBytes, path);
                    System.out.println("Exported save file: " + path);
                }
                export = false;
            }

        } catch (Exception e) {
            System.out.println("Invalid input");
            System.exit(1);
        }
    }

    public static void writeFile(byte[] fileBytes, String path) {
        try {
            FileOutputStream fos = new FileOutputStream(path);
            fos.write(fileBytes);
            fos.close();
        } catch (IOException e) {
            System.out.println("An error occurred while writing the file: " + e.getMessage());
        }
    }

    private final List<byte[]> saveList;
    private final byte[] footer;
    private final byte[] header;

    //List of the file slots of every block from every regular save file (backups ignored)
    private final List<List<Integer>> usedSlots;

    public KSSU(byte[] bytes) {
        this.saveList = new ArrayList<>();
        int length = 0;
        for (int i = 0; i < 256; i++) {
            byte[] saveSlot = Arrays.copyOfRange(bytes, 0x100 * i, (0x100 * i) + 0x100);
            length += saveSlot.length;
            saveList.add(saveSlot);
        }
        this.header = Arrays.copyOf(saveList.get(0), saveList.get(0).length);
        this.footer = Arrays.copyOfRange(bytes, length, bytes.length);
        //The used save file slots are stored
        this.usedSlots = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<Integer> currentSlot = new ArrayList<>();
            currentSlot.add(getIndexBlock(i, 0));
            currentSlot.add(getIndexBlock(i, 1));
            currentSlot.add(getIndexBlock(i, 2));
            currentSlot.add(getIndexBlock(i, 3));
            usedSlots.add(currentSlot);
        }
    }

    public String offsetReport() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            text.append("Save Slot ").append(i + 1).append(":\n");
            for (int k = 0; k < usedSlots.get(i).size(); k++) {
                int offset = 0x100 * usedSlots.get(i).get(k);
                text.append("Block ").append(k + 1).append(": ");
                text.append("From 0x");
                text.append(Integer.toHexString(offset));
                text.append(" to 0x").append(Integer.toHexString(offset + 0x100 - 1));
                text.append("\n");
            }
            text.append("\n");
        }
        return String.valueOf(text.substring(0, text.length() - 2));
    }

    public void copySlot(int slotToCopy, int slotToPaste) {
        List<Integer> sourceSlots = usedSlots.get(slotToCopy);
        List<Integer> targetSlots = usedSlots.get(slotToPaste);

        for (int i = 0; i < 4; i++) {
            //Block to copy
            byte[] sourceBlock = new byte[0x100];
            System.arraycopy(saveList.get(sourceSlots.get(i)), 0, sourceBlock, 0, sourceBlock.length);
            //Block to override
            byte[] targetBlock = new byte[0x100];
            System.arraycopy(saveList.get(targetSlots.get(i)), 0, targetBlock, 0, targetBlock.length);
            //All the data is copied except the footer
            System.arraycopy(sourceBlock, 0, targetBlock, 0, 0xF0);
            //The save slot ID is fixed
            targetBlock[0xF8] = (byte) (slotToPaste & 0xFF);
            //The block is overwritten
            saveList.set(targetSlots.get(i), targetBlock);
        }
    }

    private int getIndexBlock(int saveSlot, int blockSlot) {
        for (int i = 1; i < saveList.size(); i++) {
            int thisSaveSlot = saveList.get(i)[0xF8] & 0xFF;
            int thisBlockSlot = saveList.get(i)[0xF9] & 0xFF;
            if (thisSaveSlot == saveSlot && thisBlockSlot == blockSlot) {
                boolean isUsed = ((saveList.get(i)[0xFB] & 0xFF) == 0);
                if (isUsed) {
                    //System.out.println("Offset: " + Integer.toHexString(i * 0x100) + " File Slot: " + i);
                    return i;
                }
            }
        }
        return -1;
    }

    public void changeRegion(boolean west) {
        int valueToSet = (west) ? 0x4 : 0x3;
        int valueChange = (west) ? 0x3 : 0x4;
        for (int i = 0; i < saveList.size(); i++) {
            int header = saveList.get(i)[0x0];
            if (header == valueChange) {
                saveList.get(i)[0x0] = (byte) (valueToSet & 0xFF);
                //System.out.println("Block edited: " + i + " Offset: " + Integer.toHexString(i * 0x100));
            }
        }
    }

    private void fixChecksumSlot(byte[] block) {
        int first = 0x42, second = 0x86, third = 0x53, fourth = 0x97;
        for (int k = 0; k < block.length - 0x4; k++) {
            //Sums all the 4 bytes
            int currentByte = block[k] & 0xFF;
            switch (k % 4) {
                case 0 -> first += currentByte;
                case 1 -> second += currentByte;
                case 2 -> third += currentByte;
                case 3 -> fourth += currentByte;
            }
        }
        //Fix the 4 bytes to have a value from 0-FF
        while (first >= 256 || second >= 256 || third >= 256 || fourth >= 256) {
            while (first >= 256) {
                first -= 256;
                second++;
            }
            while (second >= 256) {
                second -= 256;
                third++;
            }
            while (third >= 256) {
                third -= 256;
                fourth++;
            }
            while (fourth >= 256) {
                fourth -= 256;
            }
        }
        int checksumOffset = 0xFC;
        block[checksumOffset] = (byte) (first & 0xFF);
        block[checksumOffset + 1] = (byte) (second & 0xFF);
        block[checksumOffset + 2] = (byte) (third & 0xFF);
        block[checksumOffset + 3] = (byte) (fourth & 0xFF);
        String checksum = "Checksums: " +
                Integer.toHexString(first) + " " +
                Integer.toHexString(second) + " " +
                Integer.toHexString(third) + " " +
                Integer.toHexString(fourth);
        //System.out.println(checksum);
    }

    public void fixAllChecksums() {
        for (byte[] bytes : saveList) fixChecksumSlot(bytes);
    }

    public byte[] getBytes() {
        fixAllChecksums();
        saveList.set(0, header); //The header checksum must not be edited
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            for (byte[] bytes : saveList) outputStream.write(bytes);
            outputStream.write(footer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return outputStream.toByteArray();
    }
}

