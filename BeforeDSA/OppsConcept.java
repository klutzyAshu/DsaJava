class Students {

    private String nome;
    private int age;

    Students(String nome, int age) {
        this.nome = nome;
        this.age = age;
    }

    public void display() {
        System.out.println("Name: " + nome);
        System.out.println("Age: " + age);
    }
}

public class OppsConcept {

    public static void main(String[] args) {

        Students s1 = new Students("Ashu", 20);

        s1.display();
    }
}