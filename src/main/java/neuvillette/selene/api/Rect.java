package neuvillette.selene.api;

public class Rect {
    public int x;
    public int y;
    public int w;
    public int h;
    private Rect(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public static Rect from_xywh(int x, int y, int w, int h) {
        return new Rect(x, y, w, h);
    }

    public static Rect from_x1y1x2y2(int x1, int y1, int x2, int y2) {
        if (x1 > x2){
            int temp = x1;
            x1 = x2;
            x2 = temp;
        }
        if (y1 > y2){
            int temp = y1;
            y1 = y2;
            y2 = temp;
        }
        return new Rect(x1, y1, x2 - x1, y2 - y1);
    }

    public int x(){
        return x;
    }

    public int y(){
        return y;
    }

    public int width(){
        return w;
    }

    public int height() {
        return h;
    }

    public int x2(){
        return x + w;
    }

    public int y2(){
        return y + h;
    }
}
