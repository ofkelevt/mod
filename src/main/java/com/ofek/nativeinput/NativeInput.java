package com.ofek.nativeinput;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.BaseTSD;

public final class NativeInput {

    private NativeInput() {}

    public static final int LEFTDOWN  = 0x0002;
    public static final int LEFTUP    = 0x0004;
    public static final int RIGHTDOWN = 0x0008;
    public static final int RIGHTUP   = 0x0010;
    public static final int KEYEVENTF_KEYUP = 0x0002;

    private static void send(WinUser.INPUT[] inputs) {
        WinDef.DWORD sent = User32.INSTANCE.SendInput(new WinDef.DWORD(inputs.length), inputs, inputs[0].size());
    }

    public static void leftClick() {
        WinUser.INPUT[] inputs = (WinUser.INPUT[]) new WinUser.INPUT().toArray(2);
        inputs[0].type = new WinDef.DWORD(WinUser.INPUT.INPUT_MOUSE);
        inputs[0].input.setType("mi");
        inputs[0].input.mi.dwFlags = new WinDef.DWORD(LEFTDOWN);
        inputs[0].input.mi.dwExtraInfo = new BaseTSD.ULONG_PTR(0);

        inputs[1].type = new WinDef.DWORD(WinUser.INPUT.INPUT_MOUSE);
        inputs[1].input.setType("mi");
        inputs[1].input.mi.dwFlags = new WinDef.DWORD(LEFTUP);
        inputs[1].input.mi.dwExtraInfo = new BaseTSD.ULONG_PTR(0);

        send(inputs);
    }

    public static void rightClick() {
        WinUser.INPUT[] inputs = (WinUser.INPUT[]) new WinUser.INPUT().toArray(2);
        inputs[0].type = new WinDef.DWORD(WinUser.INPUT.INPUT_MOUSE);
        inputs[0].input.setType("mi");
        inputs[0].input.mi.dwFlags = new WinDef.DWORD(RIGHTDOWN);
        inputs[0].input.mi.dwExtraInfo = new BaseTSD.ULONG_PTR(0);

        inputs[1].type = new WinDef.DWORD(WinUser.INPUT.INPUT_MOUSE);
        inputs[1].input.setType("mi");
        inputs[1].input.mi.dwFlags = new WinDef.DWORD(RIGHTUP);
        inputs[1].input.mi.dwExtraInfo = new BaseTSD.ULONG_PTR(0);

        send(inputs);
    }

    public static void pressKey(int keyCode) {
        WinUser.INPUT[] inputs = (WinUser.INPUT[]) new WinUser.INPUT().toArray(2);
        inputs[0].type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        inputs[0].input.setType("ki");
        inputs[0].input.ki.wVk = new WinDef.WORD(keyCode);
        inputs[0].input.ki.dwFlags = new WinDef.DWORD(0);

        inputs[1].type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        inputs[1].input.setType("ki");
        inputs[1].input.ki.wVk = new WinDef.WORD(keyCode);
        inputs[1].input.ki.dwFlags = new WinDef.DWORD(KEYEVENTF_KEYUP);

        send(inputs);
    }

    public static void holdKey(int keyCode, long holdTimeMs) {
        WinUser.INPUT down = new WinUser.INPUT();
        down.type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        down.input.setType("ki");
        down.input.ki.wVk = new WinDef.WORD(keyCode);
        down.input.ki.dwFlags = new WinDef.DWORD(0);
        send(new WinUser.INPUT[]{down});

        try {
            Thread.sleep(holdTimeMs);
        } catch (InterruptedException ignored) {}

        WinUser.INPUT up = new WinUser.INPUT();
        up.type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        up.input.setType("ki");
        up.input.ki.wVk = new WinDef.WORD(keyCode);
        up.input.ki.dwFlags = new WinDef.DWORD(KEYEVENTF_KEYUP);
        send(new WinUser.INPUT[]{up});
    }
    public static void custom(int[] codes, int[] type){
        if(codes.length != type.length) throw new IllegalArgumentException("codes and type must be the same length");
        WinUser.INPUT[] inputs = (WinUser.INPUT[]) new WinUser.INPUT().toArray(codes.length);
        for(int i = 0; i < codes.length; i++){
            if(type[i] == 0){
                inputs[i].type = new WinDef.DWORD(WinUser.INPUT.INPUT_MOUSE);
                inputs[i].input.setType("mi");
                inputs[i].input.mi.dwFlags = new WinDef.DWORD(codes[i]);
                inputs[i].input.mi.dwExtraInfo = new BaseTSD.ULONG_PTR(0);
            } else {
                inputs[i].type = new WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD);
                inputs[i].input.setType("ki");
                inputs[i].input.ki.wVk = new WinDef.WORD(codes[i]);
                inputs[i].input.ki.dwFlags = new WinDef.DWORD(type[i] == KEYEVENTF_KEYUP ? KEYEVENTF_KEYUP : 0);
            }
        }
        send(inputs);
    }
}
